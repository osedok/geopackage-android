package mil.nga.geopackage.test.tiles.retriever;

import android.graphics.Bitmap;

import junit.framework.TestCase;

import org.junit.Test;

import java.sql.SQLException;

import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.io.BitmapConverter;
import mil.nga.geopackage.test.TestConstants;
import mil.nga.geopackage.test.LoadGeoPackageTestCase;
import mil.nga.geopackage.tiles.TileBoundingBoxUtils;
import mil.nga.geopackage.tiles.retriever.GeoPackageTile;
import mil.nga.geopackage.tiles.retriever.TileCreator;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.sf.proj.Projection;
import mil.nga.sf.proj.ProjectionConstants;
import mil.nga.sf.proj.ProjectionFactory;

/**
 * Test Tile Creator from a GeoPackage with tiles
 *
 * @author osbornb
 */
public class TileCreatorGetTileTest extends LoadGeoPackageTestCase {

    /**
     * Constructor
     */
    public TileCreatorGetTileTest() {
        super(TestConstants.TILES_DB_NAME, TestConstants.TILES_DB_FILE_NAME);
    }

    /**
     * Test get tile
     *
     * @throws SQLException
     */
    @Test
    public void testGetTile() throws SQLException {

        TileDao tileDao = geoPackage.getTileDao(TestConstants.TILES_DB_TABLE_NAME);
        TestCase.assertEquals(tileDao.getProjection().getAuthority(),
                ProjectionConstants.AUTHORITY_EPSG);
        TestCase.assertEquals(Long.parseLong(tileDao.getProjection().getCode()),
                ProjectionConstants.EPSG_WEB_MERCATOR);

        tileDao.adjustTileMatrixLengths();

        Projection wgs84 = ProjectionFactory
                .getProjection(ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM);

        int width = 256;
        int height = 140;
        TileCreator tileCreator = new TileCreator(tileDao, width, height, wgs84);

        BoundingBox boundingBox = new BoundingBox();
        boundingBox = TileBoundingBoxUtils.boundWgs84BoundingBoxWithWebMercatorLimits(boundingBox);
        TestCase.assertFalse(tileCreator.hasTile(boundingBox));

        boundingBox = new BoundingBox(-180.0, 0.0, 0.0, 90.0);
        boundingBox = TileBoundingBoxUtils.boundWgs84BoundingBoxWithWebMercatorLimits(boundingBox);
        TestCase.assertTrue(tileCreator.hasTile(boundingBox));

        GeoPackageTile tile = tileCreator.getTile(boundingBox);

        TestCase.assertNotNull(tile);
        TestCase.assertEquals(width, tile.getWidth());
        TestCase.assertEquals(height, tile.getHeight());

        byte[] tileBytes = tile.getData();
        TestCase.assertNotNull(tileBytes);
        Bitmap bitmap = BitmapConverter.toBitmap(tileBytes);

        TestCase.assertEquals(width, bitmap.getWidth());
        TestCase.assertEquals(height, bitmap.getHeight());
        validateBitmap(bitmap);

        boundingBox = new BoundingBox(-90.0, 0.0, 0.0, 45.0);
        TestCase.assertTrue(tileCreator.hasTile(boundingBox));

        tile = tileCreator.getTile(boundingBox);

        TestCase.assertNotNull(tile);
        TestCase.assertEquals(width, tile.getWidth());
        TestCase.assertEquals(height, tile.getHeight());

        tileBytes = tile.getData();
        TestCase.assertNotNull(tileBytes);
        bitmap = BitmapConverter.toBitmap(tileBytes);

        TestCase.assertEquals(width, bitmap.getWidth());
        TestCase.assertEquals(height, bitmap.getHeight());
        validateBitmap(bitmap);
    }

    /**
     * Validate that the bitmap has no transparency
     *
     * @param bitmap
     */
    private void validateBitmap(Bitmap bitmap) {

        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                TestCase.assertTrue(bitmap.getPixel(x, y) != 0);
            }
        }

    }

}
