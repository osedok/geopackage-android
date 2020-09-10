package mil.nga.geopackage.test.features.columns;

import org.junit.Test;

import java.sql.SQLException;

import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.test.ImportGeoPackageTestCase;

import static org.junit.Assert.fail;

/**
 * Test Geometry Columns from an imported database
 *
 * @author osbornb
 */
public class GeometryColumnsImportTest extends ImportGeoPackageTestCase {

    /**
     * Constructor
     */
    public GeometryColumnsImportTest() {

    }

    /**
     * Test reading
     *
     * @throws SQLException
     */
    @Test
    public void testRead() throws SQLException {

        GeometryColumnsUtils.testRead(geoPackage, null);

    }

    /**
     * Test reading using the SQL/MM view
     *
     * @throws SQLException
     */
    @Test
    public void testSqlMmRead() throws SQLException {

        GeometryColumnsUtils.testSqlMmRead(geoPackage, null);

    }

    /**
     * Test reading using the SF/SQL view
     *
     * @throws SQLException
     */
    @Test
    public void testSfSqlRead() throws SQLException {

        GeometryColumnsUtils.testSfSqlRead(geoPackage, null);

    }

    /**
     * Test updating
     *
     * @throws SQLException
     */
    @Test
    public void testUpdate() throws SQLException {

        GeometryColumnsUtils.testUpdate(geoPackage);

    }

    /**
     * Test creating
     *
     * @throws SQLException
     */
    @Test
    public void testCreate() throws SQLException {

        GeometryColumnsUtils.testCreate(geoPackage);

    }

    /**
     * Test deleting
     *
     * @throws SQLException
     */
    @Test
    public void testDelete() throws SQLException {

        GeometryColumnsUtils.testDelete(geoPackage);

    }

}
