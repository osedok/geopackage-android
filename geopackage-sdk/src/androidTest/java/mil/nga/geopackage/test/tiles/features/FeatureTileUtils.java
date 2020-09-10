package mil.nga.geopackage.test.tiles.features;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;

import java.sql.SQLException;
import java.util.Date;

import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.contents.Contents;
import mil.nga.geopackage.contents.ContentsDao;
import mil.nga.geopackage.db.TableColumnKey;
import mil.nga.geopackage.features.columns.GeometryColumns;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.features.user.FeatureTableMetadata;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.tiles.features.DefaultFeatureTiles;
import mil.nga.geopackage.tiles.features.FeatureTilePointIcon;
import mil.nga.geopackage.tiles.features.FeatureTiles;
import mil.nga.sf.GeometryType;
import mil.nga.sf.LineString;
import mil.nga.sf.Point;
import mil.nga.sf.Polygon;
import mil.nga.sf.proj.ProjectionConstants;

/**
 * Feature Tile Utils
 */
public class FeatureTileUtils {

    /**
     * Create feature dao
     *
     * @return
     */
    public static FeatureDao createFeatureDao(GeoPackage geoPackage) {

        BoundingBox boundingBox = new BoundingBox();

        GeometryColumns geometryColumns = new GeometryColumns();
        geometryColumns.setId(new TableColumnKey("feature_tiles",
                "geom"));
        geometryColumns.setGeometryType(GeometryType.GEOMETRY);
        geometryColumns.setZ((byte) 0);
        geometryColumns.setM((byte) 0);
        geometryColumns
                .setSrsId(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);

        geoPackage.createFeatureTable(
                FeatureTableMetadata.create(geometryColumns, boundingBox));

        FeatureDao featureDao = geoPackage.getFeatureDao(geometryColumns);

        return featureDao;
    }

    /**
     * Insert features
     *
     * @param featureDao
     * @return number of features
     */
    public static int insertFeatures(GeoPackage geoPackage, FeatureDao featureDao) throws SQLException {

        int count = 0;

        count += 5;
        insertPoint(featureDao, 0, 0);
        insertPoint(featureDao, 0, ProjectionConstants.WEB_MERCATOR_MAX_LAT_RANGE - 1);
        insertPoint(featureDao, 0, ProjectionConstants.WEB_MERCATOR_MIN_LAT_RANGE + 1);
        insertPoint(featureDao, -179, 0);
        insertPoint(featureDao, 179, 0);

        count += 4;
        insertFourPoints(featureDao, 179, ProjectionConstants.WEB_MERCATOR_MAX_LAT_RANGE - 1);
        count += 4;
        insertFourPoints(featureDao, 90, 45);

        count += 4;
        insertFourLines(featureDao, new double[][]{{135.0, 67.5}, {90.0, 45.0}, {135.0, 45.0}});

        count += 4;
        insertFourPolygons(featureDao, new double[][]{{60.0, 35.0}, {65.0, 15.0}, {15.0, 20.0}, {20.0, 40.0}}, new double[][]{{50.0, 30.0}, {48.0, 22.0}, {30.0, 23.0}, {25.0, 34.0}});

        updateLastChange(geoPackage, featureDao);

        return count;
    }

    /**
     * Create a new feature tiles
     *
     * @return
     */
    public static FeatureTiles createFeatureTiles(Context context, GeoPackage geoPackage, FeatureDao featureDao, boolean useIcon) {

        FeatureTiles featureTiles = new DefaultFeatureTiles(context, featureDao, context.getResources().getDisplayMetrics().density);

        Paint pointPaint = featureTiles.getPointPaint();
        if (useIcon) {
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), mil.nga.geopackage.test.R.drawable.ic_launcher);
            bitmap = Bitmap.createScaledBitmap(bitmap, 25, 25, false);
            FeatureTilePointIcon icon = new FeatureTilePointIcon(bitmap);
            featureTiles.setPointIcon(icon);
        } else {
            pointPaint.setColor(Color.BLUE);
        }

        Paint linePaint = featureTiles.getLinePaintCopy();
        linePaint.setColor(Color.GREEN);
        featureTiles.setLinePaint(linePaint);

        Paint polygonPaint = featureTiles.getPolygonPaintCopy();
        polygonPaint.setColor(Color.RED);
        featureTiles.setPolygonPaint(polygonPaint);

        featureTiles.setFillPolygon(true);
        Paint polygonFillPaint = featureTiles.getPolygonFillPaintCopy();
        polygonFillPaint.setColor(Color.RED);
        polygonFillPaint.setAlpha(50);
        featureTiles.setPolygonFillPaint(polygonFillPaint);

        featureTiles.calculateDrawOverlap();

        return featureTiles;
    }

    public static void insertFourPoints(FeatureDao featureDao, double x, double y) {
        insertPoint(featureDao, x, y);
        insertPoint(featureDao, x, -1 * y);
        insertPoint(featureDao, -1 * x, y);
        insertPoint(featureDao, -1 * x, -1 * y);
    }

    public static void insertFourLines(FeatureDao featureDao, double[][] points) {
        insertLine(featureDao, convertPoints(points, false, false));
        insertLine(featureDao, convertPoints(points, true, false));
        insertLine(featureDao, convertPoints(points, false, true));
        insertLine(featureDao, convertPoints(points, true, true));
    }

    public static void insertFourPolygons(FeatureDao featureDao, double[][]... points) {
        insertPolygon(featureDao, convertPoints(false, false, points));
        insertPolygon(featureDao, convertPoints(true, false, points));
        insertPolygon(featureDao, convertPoints(false, true, points));
        insertPolygon(featureDao, convertPoints(true, true, points));
    }

    private static double[][] convertPoints(double[][] points, boolean negativeX, boolean negativeY) {

        double[][] newPoints = new double[points.length][2];
        for (int i = 0; i < points.length; i++) {
            newPoints[i][0] = negativeX ? points[i][0] * -1 : points[i][0];
            newPoints[i][1] = negativeY ? points[i][1] * -1 : points[i][1];
        }

        return newPoints;
    }

    private static double[][][] convertPoints(boolean negativeX, boolean negativeY, double[][]... points) {

        double[][][] newPoints = new double[points.length][][];
        for (int i = 0; i < points.length; i++) {
            newPoints[i] = convertPoints(points[i], negativeX, negativeY);
        }

        return newPoints;
    }

    public static long insertPoint(FeatureDao featureDao, double x, double y) {
        FeatureRow featureRow = featureDao.newRow();
        setPoint(featureRow, x, y);
        return featureDao.insert(featureRow);
    }

    public static void setPoint(FeatureRow featureRow, double x, double y) {
        GeoPackageGeometryData geomData = GeoPackageGeometryData.create(
                ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM,
                new Point(x, y));
        featureRow.setGeometry(geomData);
    }

    public static long insertLine(FeatureDao featureDao, double[][] points) {
        FeatureRow featureRow = featureDao.newRow();
        GeoPackageGeometryData geomData = GeoPackageGeometryData.create(
                ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM,
                getLineString(points));
        featureRow.setGeometry(geomData);
        return featureDao.insert(featureRow);
    }

    private static LineString getLineString(double[][] points) {
        LineString lineString = new LineString(false, false);
        for (int i = 0; i < points.length; i++) {
            Point point = new Point(false, false, points[i][0], points[i][1]);
            lineString.addPoint(point);
        }
        return lineString;
    }

    public static long insertPolygon(FeatureDao featureDao, double[][]... points) {
        FeatureRow featureRow = featureDao.newRow();
        Polygon polygon = new Polygon();
        GeoPackageGeometryData geomData = GeoPackageGeometryData.create(
                ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM, polygon);
        for (double[][] ring : points) {
            LineString lineString = getLineString(ring);
            polygon.addRing(lineString);
        }
        featureRow.setGeometry(geomData);
        return featureDao.insert(featureRow);
    }

    public static void updateLastChange(GeoPackage geoPackage, FeatureDao featureDao) throws SQLException {
        Contents contents = featureDao.getContents();
        contents.setLastChange(new Date());
        ContentsDao contentsDao = geoPackage.getContentsDao();
        contentsDao.update(contents);
    }

}
