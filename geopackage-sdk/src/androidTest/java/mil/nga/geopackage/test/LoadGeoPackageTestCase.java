package mil.nga.geopackage.test;

import org.junit.After;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.GeoPackageFactory;

/**
 * Abstract Test Case for loading a GeoPackage for tests
 *
 * @author osbornb
 */
public abstract class LoadGeoPackageTestCase extends GeoPackageTestCase {

    private final String name;
    private final String file;

    /**
     * Constructor
     */
    public LoadGeoPackageTestCase(String name, String file) {
        this.name = name;
        this.file = file;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException
     * @throws SQLException
     */
    @Override
    protected GeoPackage getGeoPackage() throws Exception {
        GeoPackageManager manager = GeoPackageFactory.getManager(activity);

        // Delete
        manager.delete(name);

        // Copy the test db file from assets to the internal storage
        TestUtils.copyAssetFileToInternalStorage(activity, testContext,
                file);

        // Import
        String importLocation = TestUtils.getAssetFileInternalStorageLocation(
                activity, file);
        manager.importGeoPackage(new File(importLocation));

        // Open
        GeoPackage geoPackage = manager.open(name);
        if (geoPackage == null) {
            throw new GeoPackageException("Failed to open database");
        }

        return geoPackage;
    }

    @After
    public void tearDown() throws Exception {

        // Close
        if (geoPackage != null) {
            geoPackage.close();
        }

        // Delete
        GeoPackageManager manager = GeoPackageFactory.getManager(activity);
        manager.delete(name);

    }

}
