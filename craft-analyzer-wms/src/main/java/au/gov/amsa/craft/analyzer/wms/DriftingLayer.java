package au.gov.amsa.craft.analyzer.wms;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observer;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.observables.GroupedObservable;
import rx.schedulers.Schedulers;
import au.gov.amsa.ais.LineAndTime;
import au.gov.amsa.ais.rx.Streams;
import au.gov.amsa.navigation.DriftingDetector;
import au.gov.amsa.navigation.VesselClass;
import au.gov.amsa.navigation.VesselPosition;
import au.gov.amsa.navigation.ais.AisVesselPositions;
import au.gov.amsa.navigation.ais.SortOperator;

import com.github.davidmoten.grumpy.projection.Projector;
import com.github.davidmoten.grumpy.wms.Layer;
import com.github.davidmoten.grumpy.wms.LayerFeatures;
import com.github.davidmoten.grumpy.wms.WmsRequest;
import com.github.davidmoten.grumpy.wms.WmsUtil;
import com.github.davidmoten.rx.Functions;
import com.google.common.base.Preconditions;

public class DriftingLayer implements Layer {

	private static Logger log = LoggerFactory.getLogger(AnalyzeLayer.class);

	private final ConcurrentLinkedQueue<VesselPosition> queue = new ConcurrentLinkedQueue<VesselPosition>();

	public DriftingLayer() {

		// collect drifting candidates

		// use these filenames as input NMEA
		List<String> filenames = getFilenames();

		Observable<VesselPosition> aisPositions = getPositions(filenames);

		new DriftingDetector().getCandidates(aisPositions)
		// group by id and date
				.groupBy(byDay())
				// grab the first position each day by ship identifier
				.flatMap(first())
				// add to queue
				.doOnNext(addToQueue())
				// run in background
				.subscribeOn(Schedulers.io())
				// subscribe
				.subscribe(createObserver());
	}

	private static Observable<VesselPosition> getPositions(
			List<String> filenames) {
		Observable<VesselPosition> aisPositions = Observable.from(filenames)
		// get the positions from each file
				.flatMap(filenameToPositions())
				// log
				.doOnNext(new Action1<VesselPosition>() {
					long count = 0;

					@Override
					public void call(VesselPosition t1) {
						if (++count % 100 == 0)
							System.out.println(t1);

					}
				})
		// only class A vessels
		// .filter(onlyClassA())
		// ignore vessels at anchor
		// .filter(notAtAnchor())
		// is a big vessel
		// .filter(isBig())
		;
		return aisPositions;
	}

	private static List<String> getFilenames() {
		List<String> filenames = new ArrayList<String>();
		final String filenameBase = "/media/analysis/nmea/2014/NMEA_ITU_201407";
		for (int i = 1; i <= 2; i++) {
			String filename = filenameBase + new DecimalFormat("00").format(i)
					+ ".gz";
			if (new File(filename).exists()) {
				filenames.add(filename);
				log.info("adding filename " + filename);
			}
		}
		return filenames;
	}

	private static Func1<VesselPosition, Boolean> isBig() {
		return new Func1<VesselPosition, Boolean>() {
			@Override
			public Boolean call(VesselPosition p) {
				return p.lengthMetres().isPresent()
						&& p.lengthMetres().get() > 50;
			}
		};
	}

	private static Func1<VesselPosition, Boolean> onlyClassA() {
		return new Func1<VesselPosition, Boolean>() {
			@Override
			public Boolean call(VesselPosition p) {
				return p.cls() == VesselClass.A;
			}
		};
	}

	private static Func1<VesselPosition, Boolean> notAtAnchor() {
		return new Func1<VesselPosition, Boolean>() {
			@Override
			public Boolean call(VesselPosition p) {
				return !p.isAtAnchor();
			}
		};
	}

	private static Func1<String, Observable<VesselPosition>> filenameToPositions() {
		return new Func1<String, Observable<VesselPosition>>() {
			@Override
			public Observable<VesselPosition> call(final String filename) {
				log.info("loading " + filename);
				return AisVesselPositions
				// read positions
						.positions(Streams.nmeaFromGzip(filename))
						// backpressure strategy - don't
						.onBackpressureBuffer()
						// in background thread from pool per file
						.subscribeOn(Schedulers.computation())
						// log completion of read of file
						.doOnCompleted(new Action0() {
							@Override
							public void call() {
								log.info("finished " + filename);
							}
						});
			}
		};
	}

	private Observer<VesselPosition> createObserver() {
		return new Observer<VesselPosition>() {

			@Override
			public void onCompleted() {
				System.out.println("done");
			}

			@Override
			public void onError(Throwable e) {
				log.error(e.getMessage(), e);
			}

			@Override
			public void onNext(VesselPosition t) {
				// do nothing
			}
		};
	}

	private Action1<VesselPosition> addToQueue() {
		return new Action1<VesselPosition>() {

			@Override
			public void call(VesselPosition p) {
				// System.out.println(p.lat() + "\t" + p.lon() + "\t"
				// + p.id().uniqueId());
				// System.out.println(p);
				queue.add(p);
				System.out.println("queue size=" + queue.size());
			}
		};
	}

	private Func1<GroupedObservable<String, VesselPosition>, Observable<VesselPosition>> first() {
		return new Func1<GroupedObservable<String, VesselPosition>, Observable<VesselPosition>>() {

			@Override
			public Observable<VesselPosition> call(
					GroupedObservable<String, VesselPosition> positions) {
				return positions.first();
			}
		};
	}

	private Func1<VesselPosition, String> byDay() {
		return new Func1<VesselPosition, String>() {
			@Override
			public String call(VesselPosition p) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				return p.id().uniqueId() + sdf.format(new Date(p.time()));
			}
		};
	}

	public static final Func2<VesselPosition, VesselPosition, Integer> SORT_BY_TIME = new Func2<VesselPosition, VesselPosition, Integer>() {

		@Override
		public Integer call(VesselPosition p1, VesselPosition p2) {
			return ((Long) p1.time()).compareTo(p2.time());
		}
	};

	@Override
	public LayerFeatures getFeatures() {
		return LayerFeatures.builder().crs("EPSG:4326").crs("EPSG:3857")
				.name("Drifting").queryable().build();
	}

	@Override
	public String getInfo(Date time, WmsRequest request, final Point point,
			String mimeType) {

		final int HOTSPOT_SIZE = 5;

		final Projector projector = WmsUtil.getProjector(request);
		final StringBuilder response = new StringBuilder();
		response.append("<html>");
		Observable.from(queue)
		// only vessel positions close to the click point
				.filter(new Func1<VesselPosition, Boolean>() {
					@Override
					public Boolean call(VesselPosition p) {
						Point pt = projector.toPoint(p.lat(), p.lon());
						return Math.abs(point.x - pt.x) <= HOTSPOT_SIZE
								&& Math.abs(point.y - pt.y) <= HOTSPOT_SIZE;
					}
				})
				// add html fragment for each vessel position to the response
				.doOnNext(new Action1<VesselPosition>() {
					@Override
					public void call(VesselPosition p) {
						response.append("<p>");
						response.append("<a href=\"https://www.fleetmon.com/en/vessels?s="
								+ p.id().uniqueId()
								+ "\">mmsi="
								+ p.id().uniqueId()
								+ "</a>, time="
								+ new Date(p.time()));
						response.append("</p>");
					}
				})
				// go!
				.subscribe();
		response.append("</html>");
		return response.toString();
	}

	@Override
	public void render(Graphics2D g, WmsRequest request) {
		log.info("request=" + request);
		log.info("drawing " + queue.size() + " positions");
		final Projector projector = WmsUtil.getProjector(request);
		for (VesselPosition p : queue) {
			Point point = projector.toPoint(p.lat(), p.lon());
			g.setColor(Color.red);
			g.drawRect(point.x, point.y, 1, 1);
		}
		log.info("drawn");

	}

	private static void sortFile(String filename) throws FileNotFoundException,
			IOException {
		Comparator<LineAndTime> comparator = new Comparator<LineAndTime>() {
			@Override
			public int compare(LineAndTime line1, LineAndTime line2) {
				return ((Long) line1.getTime()).compareTo(line2.getTime());
			}
		};
		final File in = new File(filename);
		final File outFile = new File(in.getParentFile(), "sorted-"
				+ in.getName());
		if (outFile.exists()) {
			log.info("file exists: " + outFile);
			return;
		}
		final OutputStreamWriter out = new OutputStreamWriter(
				new GZIPOutputStream(new FileOutputStream(outFile)),
				StandardCharsets.UTF_8);

		Streams
		// read from file
		.nmeaFromGzip(filename)
		// get time
				.flatMap(Streams.toLineAndTime())
				// sort
				.lift(new SortOperator<LineAndTime>(comparator, 20000000))
				// .lift(Logging.<LineAndTime> logger().showValue().log())
				.doOnCompleted(new Action0() {
					@Override
					public void call() {
						try {
							out.close();
						} catch (IOException e) {
						}
					}
				}).forEach(new Action1<LineAndTime>() {
					@Override
					public void call(LineAndTime line) {
						try {
							out.write(line.getLine());
							out.write('\n');
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				});
	}

	private static void sortFiles() throws FileNotFoundException, IOException {
		// String filename = "/media/analysis/nmea/2014/NMEA_ITU_20140701.gz";
		File directory = new File("/media/analysis/nmea/2014");
		Preconditions.checkArgument(directory.exists());
		File[] files = directory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.getName().startsWith("NMEA_")
						&& f.getName().endsWith(".gz");
			}
		});
		int count = 0;

		Arrays.sort(files, new Comparator<File>() {
			@Override
			public int compare(File f1, File f2) {
				return f1.getPath().compareTo(f2.getPath());
			}
		});

		for (File file : files) {
			count++;
			log.info("sorting " + count + " of " + files.length + ": " + file);
			sortFile(file.getAbsolutePath());
		}
	}

	public static void main(String[] args) throws FileNotFoundException,
			IOException, InterruptedException {

		// sortFiles();
		// Observable.range(1, 20).flatMap(
		// new Func1<Integer, Observable<Integer>>() {
		// @Override
		// public Observable<Integer> call(final Integer n) {
		// return Observable.range(1, Integer.MAX_VALUE - 1)
		// .map(new Func1<Integer, Integer>(){
		// @Override
		// public Integer call(Integer i) {
		// return i*n;
		// }})
		// .subscribeOn(Schedulers.computation());
		// }
		// }).forEach(new Action1<Integer>() {
		// long count = 0;
		// @Override
		// public void call(Integer n) {
		// if (++count %1000==0)
		// System.out.println(n);
		// }});

		Observable.range(1, 2)
		// produce 1000 strings a second per range emission
				.flatMap(new Func1<Integer, Observable<String>>() {
					@Override
					public Observable<String> call(final Integer number) {
						return Observable
								.range(1, Integer.MAX_VALUE)
								.map(new Func1<Integer, String>() {

									@Override
									public String call(Integer n) {
										// simulate something intensive
										try {
											Thread.sleep(1);
											return number
													+ "-"
													+ System.currentTimeMillis();
										} catch (InterruptedException e) {
											throw new RuntimeException(e);
										}
									}
								})
								.onBackpressureBuffer()
								.subscribeOn(Schedulers.computation());
					}
				})
				// log every 100th value
				.doOnNext(new Action1<String>() {
					long count;

					@Override
					public void call(String line) {
						if (++count % 100 == 0)
							System.out.println(line);
					}
				}).subscribeOn(Schedulers.computation()).subscribe();

		// List<String> filenames = getFilenames();
		// Observable<VesselPosition> positions = getPositions(filenames)
		// .subscribeOn(Schedulers.newThread());
		//
		// positions
		// // observeOn
		// // .observeOn(Schedulers.newThread())
		// // go
		// .subscribe();
		Thread.sleep(10000000);

	}

}
