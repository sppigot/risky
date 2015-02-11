package au.gov.amsa.risky.format;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.NetcdfFileWriter.Version;
import ucar.nc2.Variable;

public class NetcdfFixesWriter {

	public static void writeFixes(List<Fix> fixes, File file) {

		try {
			// TODO evaluate use of NetCdf structures
			NetcdfFileWriter f = NetcdfFileWriter.createNew(Version.netcdf3,
					file.getPath());

			// Create netCDF dimensions
			Dimension dimTime = f.addUnlimitedDimension("time");
			Dimension dimLat = f.addDimension(null, "latitude", fixes.size());
			Dimension dimLon = f.addDimension(null, "longitude", fixes.size());
			Dimension dimSource = f.addDimension(null, "source", fixes.size());
			Dimension dimLatency = f
					.addDimension(null, "latency", fixes.size());
			Dimension dimNavStatus = f.addDimension(null,
					"navigational_status", fixes.size());
			Dimension dimRateOfTurn = f.addDimension(null, "rate_of_turn",
					fixes.size());
			Dimension dimSpeedOverGround = f.addDimension(null,
					"speed_over_ground", fixes.size());
			Dimension dimCourseOverGround = f.addDimension(null,
					"course_over_ground", fixes.size());
			Dimension dimHeading = f
					.addDimension(null, "heading", fixes.size());
			Dimension dimAisClass = f.addDimension(null, "ais_class",
					fixes.size());

			Variable varLat = f.addVariable(null, "latitude", DataType.FLOAT,
					Arrays.asList(dimLat));
			varLat.addAttribute(new Attribute("units", "degrees_east"));
			varLat.addAttribute(new Attribute("standard_name", "latitude"));
			varLat.addAttribute(new Attribute("long_name",
					"latitude of craft position"));

			Variable varLon = f.addVariable(null, "longitude", DataType.FLOAT,
					Arrays.asList(dimLon));
			varLon.addAttribute(new Attribute("units", "degrees_north"));
			varLon.addAttribute(new Attribute("standard_name", "longitude"));
			varLon.addAttribute(new Attribute("long_name",
					"longitude of craft position"));

			Variable varTime = f.addVariable(null, "time", DataType.DOUBLE,
					Arrays.asList(dimTime));
			varTime.addAttribute(new Attribute("units",
					"days since 1970-01-01 00:00:00 UTC"));

			Variable varSource = f.addVariable(null, "source", DataType.SHORT,
					Arrays.asList(dimSource));
			varSource.addAttribute(new Attribute("encoding",
					"0=not present, 1=present, others TBA"));

			Variable varLatency = f.addVariable(null, "latency", DataType.INT,
					Arrays.asList(dimLatency));
			varLatency.addAttribute(new Attribute("units", "s"));
			varLatency
					.addAttribute(new Attribute("encoding", "-1=not present"));

			Variable varNavStatus = f.addVariable(null, "navigational_status",
					DataType.BYTE, Arrays.asList(dimNavStatus));
			varNavStatus.addAttribute(new Attribute("encoding",
					"127=not present"));

			Variable varRateOfTurn = f.addVariable(null, "rate_of_turn",
					DataType.BYTE, Arrays.asList(dimRateOfTurn));
			varRateOfTurn.addAttribute(new Attribute("encoding",
					"-128=not present, others TBA"));

			Variable varSpeedOverGround = f.addVariable(null,
					"speed_over_ground", DataType.BYTE,
					Arrays.asList(dimSpeedOverGround));
			varSpeedOverGround
					.addAttribute(new Attribute("units", "1/10 knot"));
			varSpeedOverGround.addAttribute(new Attribute("encoding",
					"1023=not present"));

			Variable varCourseOverGround = f.addVariable(null,
					"course_over_ground", DataType.BYTE,
					Arrays.asList(dimCourseOverGround));
			varCourseOverGround.addAttribute(new Attribute("units",
					"1/10 degree"));
			varCourseOverGround.addAttribute(new Attribute("encoding",
					"3600=not present"));

			Variable varHeading = f.addVariable(null, "heading", DataType.BYTE,
					Arrays.asList(dimHeading));
			varHeading.addAttribute(new Attribute("units", "degrees"));
			varHeading
					.addAttribute(new Attribute("encoding", "360=not present"));

			Variable varAisClass = f.addVariable(null, "ais_class",
					DataType.BYTE, Arrays.asList(dimAisClass));
			varAisClass.addAttribute(new Attribute("encoding", "0=A,1=B"));

			// create the file
			f.create();

			int[] shape = new int[] { fixes.size() };
			Array dataLat = Array.factory(DataType.FLOAT, shape);
			Array dataLon = Array.factory(DataType.FLOAT, shape);
			Array dataTime = Array.factory(DataType.DOUBLE, shape);
			Array dataSource = Array.factory(DataType.SHORT, shape);
			Array dataLatency = Array.factory(DataType.INT, shape);
			Array dataNavStatus = Array.factory(DataType.BYTE, shape);
			Array dataRateOfTurn = Array.factory(DataType.BYTE, shape);
			Array dataSpeedOverGround = Array.factory(DataType.SHORT, shape);
			Array dataCourseOverGround = Array.factory(DataType.SHORT, shape);
			Array dataHeading = Array.factory(DataType.SHORT, shape);
			Array dataAisClass = Array.factory(DataType.BYTE, shape);

			for (int i = 0; i < fixes.size(); i++) {
				Fix fix = fixes.get(i);
				dataLat.setFloat(i, fix.getLat());
				dataLon.setFloat(i, fix.getLon());
				double days = (double) fix.getTime()
						/ TimeUnit.DAYS.toMillis(1);
				dataTime.setDouble(i, days);
				dataSource.setShort(i,
						fix.getSource().or(BinaryFixes.SOURCE_ABSENT));
				dataLatency.setInt(i,
						fix.getLatencySeconds().or(BinaryFixes.LATENCY_ABSENT));
				int navStatus;
				if (fix.getNavigationalStatus().isPresent())
					navStatus = fix.getNavigationalStatus().get().ordinal();
				else
					navStatus = BinaryFixes.NAV_STATUS_ABSENT;
				dataNavStatus.setByte(i, (byte) navStatus);
				dataRateOfTurn.setByte(i, BinaryFixes.RATE_OF_TURN_ABSENT);
				final short sog;
				if (fix.getSpeedOverGroundKnots().isPresent())
					sog = (short) Math.round(fix.getSpeedOverGroundKnots()
							.get() * 10);
				else
					sog = 1023;
				final short cog;
				if (fix.getCourseOverGroundDegrees().isPresent())
					cog = (short) Math.round(fix.getCourseOverGroundDegrees()
							.get() * 10);
				else
					cog = 3600;
				dataCourseOverGround.setShort(i, sog);
				final short heading;
				if (fix.getCourseOverGroundDegrees().isPresent())
					heading = (short) Math.floor(fix
							.getCourseOverGroundDegrees().get() + 0.01f);
				else
					heading = (short) 360;
				dataHeading.setShort(i, heading);
				byte aisClass;
				if (fix.getAisClass() == AisClass.A)
					aisClass = (byte) 0;
				else
					aisClass = (byte) 1;
				dataAisClass.setByte(i, aisClass);
			}

			f.write(varLat, dataLat);
			f.write(varLon, dataLon);
			f.write(varTime, dataTime);
			f.write(varSource, dataSource);
			f.write(varLatency, dataLatency);
			f.write(varNavStatus, dataNavStatus);
			f.write(varRateOfTurn, dataRateOfTurn);
			f.write(varSpeedOverGround, dataSpeedOverGround);
			f.write(varCourseOverGround, dataCourseOverGround);
			f.write(varHeading, dataHeading);

		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InvalidRangeException e) {
			throw new RuntimeException(e);
		}
	}
}