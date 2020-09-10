package mil.nga.geopackage.test.tiles.features;

import org.junit.Test;

import java.io.IOException;

import mil.nga.geopackage.test.ImportGeoPackageTestCase;

/**
 * Test Feature Preview from an imported database
 *
 * @author osbornb
 */
public class FeaturePreviewImportTest extends ImportGeoPackageTestCase {

    /**
     * Constructor
     */
    public FeaturePreviewImportTest() {

    }

    /**
     * Test draw
     *
     * @throws IOException upon error
     */
    @Test
    public void testDraw() throws IOException {

        FeaturePreviewUtils.testDraw(activity, geoPackage);

    }

}
