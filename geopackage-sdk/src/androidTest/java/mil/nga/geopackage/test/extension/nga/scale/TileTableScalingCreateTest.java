package mil.nga.geopackage.test.extension.nga.scale;

import org.junit.Test;

import java.sql.SQLException;

import mil.nga.geopackage.test.CreateGeoPackageTestCase;

/**
 * Test Tile Table Scaling from a created database
 *
 * @author osbornb
 */
public class TileTableScalingCreateTest extends CreateGeoPackageTestCase {

    /**
     * Constructor
     */
    public TileTableScalingCreateTest() {

    }

    /**
     * Test link
     *
     * @throws SQLException
     */
    @Test
    public void testScaling() throws SQLException {

        TileTableScalingUtils.testScaling(geoPackage);

    }

}
