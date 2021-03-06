package au.gov.amsa.animator;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.wms.WebMapServer;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.map.WMSLayer;
import org.geotools.ows.ServiceException;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.styling.SLD;
import org.geotools.styling.Style;
import org.geotools.swing.wms.WMSLayerChooser;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

public class Map {

    private static final float CANBERRA_LAT = -35.3075f;
    private static final float CANBERRA_LONG = 149.1244f;

    public static MapContent createMap() {
        final MapContent map = new MapContent();
        map.setTitle("Animator");
        map.getViewport();
        map.addLayer(createCoastlineLayer());
        map.addLayer(createExtraFeatures());
        // addWms(map);
        return map;
    }

    private static Layer createCoastlineLayer() {
        try {
            // File file = new File(
            // "/home/dxm/Downloads/shapefile-australia-coastline-polygon/cstauscd_r.shp");
            File file = new File("src/main/resources/shapes/countries.shp");
            FileDataStore store = FileDataStoreFinder.getDataStore(file);
            SimpleFeatureSource featureSource = store.getFeatureSource();

            // Style style = SLD.createSimpleStyle(featureSource.getSchema());
            Style style = SLD.createPolygonStyle(Color.black, new Color(242, 237, 206), 1);
            Layer layer = new FeatureLayer(featureSource, style);
            return layer;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Layer createExtraFeatures() {
        SimpleFeatureTypeBuilder b = new SimpleFeatureTypeBuilder();
        b.setName("Location");
        b.setCRS(DefaultGeographicCRS.WGS84);
        // picture location
        b.add("geom", Point.class);
        final SimpleFeatureType TYPE = b.buildFeatureType();

        GeometryFactory gf = JTSFactoryFinder.getGeometryFactory();
        Point point = gf.createPoint(new Coordinate(CANBERRA_LONG, CANBERRA_LAT));

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(TYPE);
        builder.add(point);
        SimpleFeature feature = builder.buildFeature("Canberra");
        DefaultFeatureCollection features = new DefaultFeatureCollection(null, null);
        features.add(feature);

        Style style = SLD.createPointStyle("Star", Color.BLUE, Color.BLUE, 0.3f, 10);

        return new FeatureLayer(features, style);
    }

    static void addWms(MapContent map) {
        // URL wmsUrl = WMSChooser.showChooseWMS();

        WebMapServer wms;
        try {
            String url = "http://129.206.228.72/cached/osm?Request=GetCapabilities";
            // String url = "http://sarapps.amsa.gov.au:8080/cts-gis/wms";
            wms = new WebMapServer(new URL(url));
        } catch (ServiceException | IOException e) {
            throw new RuntimeException(e);
        }
        List<org.geotools.data.ows.Layer> wmsLayers = WMSLayerChooser.showSelectLayer(wms);
        for (org.geotools.data.ows.Layer wmsLayer : wmsLayers) {
            System.out.println("adding " + wmsLayer.getTitle());
            WMSLayer displayLayer = new WMSLayer(wms, wmsLayer);
            map.addLayer(displayLayer);
        }
    }
}
