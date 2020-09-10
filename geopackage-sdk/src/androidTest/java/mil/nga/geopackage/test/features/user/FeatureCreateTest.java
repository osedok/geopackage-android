package mil.nga.geopackage.test.features.user;

import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;

import mil.nga.geopackage.test.CreateGeoPackageTestCase;

/**
 * Test Features from a created database
 *
 * @author osbornb
 */
public class FeatureCreateTest extends CreateGeoPackageTestCase {

    /**
     * Constructor
     */
    public FeatureCreateTest() {

    }

    /**
     * Test reading
     *
     * @throws SQLException upon error
     * @throws IOException  upon error
     */
    @Test
    public void testRead() throws SQLException, IOException {

        FeatureUtils.testRead(geoPackage);

    }

    /**
     * Test updating
     *
     * @throws SQLException upon error
     */
    @Test
    public void testUpdate() throws SQLException {

        FeatureUtils.testUpdate(geoPackage);

    }

    /**
     * Test updating with added columns
     *
     * @throws SQLException upon error
     */
    @Test
    public void testUpdateAddColumns() throws SQLException {

        FeatureUtils.testUpdateAddColumns(geoPackage);

    }

    /**
     * Test creating
     *
     * @throws SQLException upon error
     */
    @Test
    public void testCreate() throws SQLException {

        FeatureUtils.testCreate(geoPackage);

    }

    /**
     * Test deleting
     *
     * @throws SQLException upon error
     */
    @Test
    public void testDelete() throws SQLException {

        FeatureUtils.testDelete(geoPackage);

    }

    /**
     * Test Feature DAO primary key modifications and disabling value validation
     *
     * @throws SQLException upon error
     */
    @Test
    public void testPkModifiableAndValueValidation() throws SQLException {

        FeatureUtils.testPkModifiableAndValueValidation(geoPackage);

    }

}
