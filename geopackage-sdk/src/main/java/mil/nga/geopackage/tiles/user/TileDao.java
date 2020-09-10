package mil.nga.geopackage.tiles.user;

import androidx.collection.LongSparseArray;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.contents.Contents;
import mil.nga.geopackage.db.GeoPackageConnection;
import mil.nga.geopackage.srs.SpatialReferenceSystem;
import mil.nga.geopackage.tiles.TileBoundingBoxUtils;
import mil.nga.geopackage.tiles.TileGrid;
import mil.nga.geopackage.tiles.matrix.TileMatrix;
import mil.nga.geopackage.tiles.matrixset.TileMatrixSet;
import mil.nga.geopackage.user.UserDao;
import mil.nga.sf.proj.Projection;
import mil.nga.sf.proj.ProjectionConstants;

/**
 * Tile DAO for reading tile user tables
 *
 * @author osbornb
 */
public class TileDao extends UserDao<TileColumn, TileTable, TileRow, TileCursor> {

    /**
     * Tile connection
     */
    private final TileConnection tileDb;

    /**
     * Tile Matrix Set
     */
    private final TileMatrixSet tileMatrixSet;

    /**
     * Tile Matrices
     */
    private final List<TileMatrix> tileMatrices;

    /**
     * Mapping between zoom levels and the tile matrix
     */
    private final LongSparseArray<TileMatrix> zoomLevelToTileMatrix = new LongSparseArray<>();

    /**
     * Min zoom
     */
    private final long minZoom;

    /**
     * Max zoom
     */
    private final long maxZoom;

    /**
     * Array of widths of the tiles at each zoom level in default units
     */
    private final double[] widths;

    /**
     * Array of heights of the tiles at each zoom level in default units
     */
    private final double[] heights;

    /**
     * Constructor
     *
     * @param database      database name
     * @param db            GeoPackage connection
     * @param tileMatrixSet tile matrix set
     * @param tileMatrices  tile matrices
     * @param table         tile table
     */
    public TileDao(String database, GeoPackageConnection db, TileMatrixSet tileMatrixSet,
                   List<TileMatrix> tileMatrices, TileTable table) {
        super(database, db, new TileConnection(db), table);

        this.tileDb = (TileConnection) getUserDb();
        this.tileMatrixSet = tileMatrixSet;
        this.tileMatrices = tileMatrices;
        this.widths = new double[tileMatrices.size()];
        this.heights = new double[tileMatrices.size()];

        projection = tileMatrixSet.getProjection();

        // Set the min and max zoom levels
        if (!tileMatrices.isEmpty()) {
            minZoom = tileMatrices.get(0).getZoomLevel();
            maxZoom = tileMatrices.get(tileMatrices.size() - 1).getZoomLevel();
        } else {
            minZoom = 0;
            maxZoom = 0;
        }

        // Populate the zoom level to tile matrix and the sorted tile widths and
        // heights
        for (int i = 0; i < tileMatrices.size(); i++) {
            TileMatrix tileMatrix = tileMatrices.get(i);
            zoomLevelToTileMatrix.put(tileMatrix.getZoomLevel(), tileMatrix);
            widths[tileMatrices.size() - i - 1] = tileMatrix.getPixelXSize()
                    * tileMatrix.getTileWidth();
            heights[tileMatrices.size() - i - 1] = tileMatrix.getPixelYSize()
                    * tileMatrix.getTileHeight();
        }

        if (tileMatrixSet.getContents() == null) {
            throw new GeoPackageException(TileMatrixSet.class.getSimpleName()
                    + " " + tileMatrixSet.getId() + " has null "
                    + Contents.class.getSimpleName());
        }
        if (tileMatrixSet.getSrs() == null) {
            throw new GeoPackageException(TileMatrixSet.class.getSimpleName()
                    + " " + tileMatrixSet.getId() + " has null "
                    + SpatialReferenceSystem.class.getSimpleName());
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BoundingBox getBoundingBox() {
        return tileMatrixSet.getBoundingBox();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BoundingBox getBoundingBox(Projection projection) {
        return tileMatrixSet.getBoundingBox(projection);
    }

    /**
     * Get the bounding box of tiles
     *
     * @param zoomLevel zoom level
     * @return bounding box of zoom level, or null if no tiles
     * @since 1.1.1
     */
    public BoundingBox getBoundingBox(long zoomLevel) {
        BoundingBox boundingBox = null;
        TileMatrix tileMatrix = getTileMatrix(zoomLevel);
        if (tileMatrix != null) {
            TileGrid tileGrid = queryForTileGrid(zoomLevel);
            if (tileGrid != null) {
                BoundingBox matrixSetBoundingBox = getBoundingBox();
                boundingBox = TileBoundingBoxUtils.getBoundingBox(
                        matrixSetBoundingBox, tileMatrix, tileGrid);
            }

        }
        return boundingBox;
    }

    /**
     * Get the tile grid of the zoom level
     *
     * @param zoomLevel zoom level
     * @return tile grid at zoom level, null if not tile matrix at zoom level
     * @since 1.1.1
     */
    public TileGrid getTileGrid(long zoomLevel) {
        TileGrid tileGrid = null;
        TileMatrix tileMatrix = getTileMatrix(zoomLevel);
        if (tileMatrix != null) {
            tileGrid = new TileGrid(0, 0, tileMatrix.getMatrixWidth() - 1,
                    tileMatrix.getMatrixHeight() - 1);
        }
        return tileGrid;
    }

    /**
     * Adjust the tile matrix lengths if needed. Check if the tile matrix width
     * and height need to expand to account for pixel * number of pixels fitting
     * into the tile matrix lengths
     */
    public void adjustTileMatrixLengths() {
        TileDaoUtils.adjustTileMatrixLengths(tileMatrixSet, tileMatrices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TileRow newRow() {
        return new TileRow(getTable());
    }

    /**
     * Get the Tile connection
     *
     * @return tile connection
     */
    public TileConnection getTileDb() {
        return tileDb;
    }

    /**
     * Get the tile matrix set
     *
     * @return tile matrix set
     */
    public TileMatrixSet getTileMatrixSet() {
        return tileMatrixSet;
    }

    /**
     * Get the tile matrices
     *
     * @return tile matrices
     */
    public List<TileMatrix> getTileMatrices() {
        return tileMatrices;
    }

    /**
     * Get the tile matrix at the zoom level
     *
     * @param zoomLevel zoom level
     * @return tile matrix
     */
    public TileMatrix getTileMatrix(long zoomLevel) {
        return zoomLevelToTileMatrix.get(zoomLevel);
    }

    /**
     * Get the Spatial Reference System
     *
     * @return srs
     * @since 4.0.0
     */
    public SpatialReferenceSystem getSrs() {
        return tileMatrixSet.getSrs();
    }

    /**
     * Get the Spatial Reference System id
     *
     * @return srs id
     * @since 4.0.0
     */
    public long getSrsId() {
        return tileMatrixSet.getSrsId();
    }

    /**
     * Get the min zoom
     *
     * @return min zoom
     */
    public long getMinZoom() {
        return minZoom;
    }

    /**
     * Get the max zoom
     *
     * @return max zoom
     */
    public long getMaxZoom() {
        return maxZoom;
    }

    /**
     * Query for a Tile
     *
     * @param column    column
     * @param row       row
     * @param zoomLevel zoom level
     * @return tile row
     */
    public TileRow queryForTile(long column, long row, long zoomLevel) {

        Map<String, Object> fieldValues = new HashMap<String, Object>();
        fieldValues.put(TileTable.COLUMN_TILE_COLUMN, column);
        fieldValues.put(TileTable.COLUMN_TILE_ROW, row);
        fieldValues.put(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel);

        TileCursor cursor = queryForFieldValues(fieldValues);
        TileRow tileRow = null;
        try {
            if (cursor.moveToNext()) {
                tileRow = cursor.getRow();
            }
        } finally {
            cursor.close();
        }

        return tileRow;
    }

    /**
     * Query for Tiles at a zoom level
     *
     * @param zoomLevel zoom level
     * @return tile cursor, should be closed
     */
    public TileCursor queryForTile(long zoomLevel) {
        return queryForEq(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel);
    }

    /**
     * Query for Tiles at a zoom level in descending row and column order
     *
     * @param zoomLevel zoom level
     * @return tile cursor, should be closed
     */
    public TileCursor queryForTileDescending(long zoomLevel) {
        return queryForEq(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel, null, null,
                TileTable.COLUMN_TILE_ROW + " DESC, "
                        + TileTable.COLUMN_TILE_COLUMN + " DESC");
    }

    /**
     * Query for Tiles at a zoom level and column
     *
     * @param column    column
     * @param zoomLevel zoom level
     * @return tile cursor
     */
    public TileCursor queryForTilesInColumn(long column, long zoomLevel) {

        Map<String, Object> fieldValues = new HashMap<String, Object>();
        fieldValues.put(TileTable.COLUMN_TILE_COLUMN, column);
        fieldValues.put(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel);

        return queryForFieldValues(fieldValues);
    }

    /**
     * Query for Tiles at a zoom level and row
     *
     * @param row       row
     * @param zoomLevel zoom level
     * @return tile cursor
     */
    public TileCursor queryForTilesInRow(long row, long zoomLevel) {

        Map<String, Object> fieldValues = new HashMap<String, Object>();
        fieldValues.put(TileTable.COLUMN_TILE_ROW, row);
        fieldValues.put(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel);

        return queryForFieldValues(fieldValues);
    }

    /**
     * Get the zoom level for the provided width and height in the default units
     *
     * @param length in default units
     * @return zoom level
     */
    public Long getZoomLevel(double length) {

        Long zoomLevel = TileDaoUtils.getZoomLevel(widths, heights, tileMatrices, length);
        return zoomLevel;
    }

    /**
     * Get the zoom level for the provided width and height in the default units
     *
     * @param width  in default units
     * @param height in default units
     * @return zoom level
     * @since 1.3.1
     */
    public Long getZoomLevel(double width, double height) {

        Long zoomLevel = TileDaoUtils.getZoomLevel(widths, heights,
                tileMatrices, width, height);
        return zoomLevel;
    }

    /**
     * Get the closest zoom level for the provided width and height in the
     * default units
     *
     * @param length in default units
     * @return zoom level
     * @since 1.3.1
     */
    public Long getClosestZoomLevel(double length) {

        Long zoomLevel = TileDaoUtils.getClosestZoomLevel(widths, heights,
                tileMatrices, length);
        return zoomLevel;
    }

    /**
     * Get the closest zoom level for the provided width and height in the
     * default units
     *
     * @param width  in default units
     * @param height in default units
     * @return zoom level
     * @since 1.3.1
     */
    public Long getClosestZoomLevel(double width, double height) {

        Long zoomLevel = TileDaoUtils.getClosestZoomLevel(widths, heights,
                tileMatrices, width, height);
        return zoomLevel;
    }

    /**
     * Get the approximate zoom level for the provided length in the default
     * units. Tiles may or may not exist for the returned zoom level. The
     * approximate zoom level is determined using a factor of 2 from the zoom
     * levels with tiles.
     *
     * @param length length in default units
     * @return approximate zoom level
     * @since 2.0.2
     */
    public Long getApproximateZoomLevel(double length) {

        Long zoomLevel = TileDaoUtils.getApproximateZoomLevel(widths, heights,
                tileMatrices, length);
        return zoomLevel;
    }

    /**
     * Get the approximate zoom level for the provided width and height in the
     * default units. Tiles may or may not exist for the returned zoom level.
     * The approximate zoom level is determined using a factor of 2 from the
     * zoom levels with tiles.
     *
     * @param width  width in default units
     * @param height height in default units
     * @return approximate zoom level
     * @since 2.0.2
     */
    public Long getApproximateZoomLevel(double width, double height) {

        Long zoomLevel = TileDaoUtils.getApproximateZoomLevel(widths, heights,
                tileMatrices, width, height);
        return zoomLevel;
    }

    /**
     * Query by tile grid and zoom level
     *
     * @param tileGrid  tile grid
     * @param zoomLevel zoom level
     * @return cursor from query or null if the zoom level tile ranges do not
     * overlap the bounding box
     */
    public TileCursor queryByTileGrid(TileGrid tileGrid, long zoomLevel) {
        return queryByTileGrid(tileGrid, zoomLevel, null);
    }

    /**
     * Query by tile grid and zoom level
     *
     * @param tileGrid  tile grid
     * @param zoomLevel zoom level
     * @param orderBy   order by
     * @return cursor from query or null if the zoom level tile ranges do not
     * overlap the bounding box
     * @since 1.3.1
     */
    public TileCursor queryByTileGrid(TileGrid tileGrid, long zoomLevel,
                                      String orderBy) {

        TileCursor tileCursor = null;

        if (tileGrid != null) {

            StringBuilder where = new StringBuilder();

            where.append(buildWhere(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel));

            where.append(" AND ");
            where.append(buildWhere(TileTable.COLUMN_TILE_COLUMN,
                    tileGrid.getMinX(), ">="));

            where.append(" AND ");
            where.append(buildWhere(TileTable.COLUMN_TILE_COLUMN,
                    tileGrid.getMaxX(), "<="));

            where.append(" AND ");
            where.append(buildWhere(TileTable.COLUMN_TILE_ROW,
                    tileGrid.getMinY(), ">="));

            where.append(" AND ");
            where.append(buildWhere(TileTable.COLUMN_TILE_ROW,
                    tileGrid.getMaxY(), "<="));

            String[] whereArgs = buildWhereArgs(new Object[]{zoomLevel,
                    tileGrid.getMinX(), tileGrid.getMaxX(), tileGrid.getMinY(),
                    tileGrid.getMaxY()});

            tileCursor = query(where.toString(), whereArgs, null, null, orderBy);
        }

        return tileCursor;
    }

    /**
     * Query for the bounding
     *
     * @param zoomLevel zoom level
     * @return tile grid of tiles at the zoom level
     * @since 1.1.1
     */
    public TileGrid queryForTileGrid(long zoomLevel) {

        String where = buildWhere(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel);
        String[] whereArgs = buildWhereArgs(new Object[]{zoomLevel});

        Number minX = min(TileTable.COLUMN_TILE_COLUMN, where, whereArgs);
        Number maxX = max(TileTable.COLUMN_TILE_COLUMN, where, whereArgs);
        Number minY = min(TileTable.COLUMN_TILE_ROW, where, whereArgs);
        Number maxY = max(TileTable.COLUMN_TILE_ROW, where, whereArgs);

        TileGrid tileGrid = null;
        if (minX != null && maxX != null && minY != null && maxY != null) {
            tileGrid = new TileGrid(minX.longValue(), minY.longValue(),
                    maxX.longValue(), maxY.longValue());
        }

        return tileGrid;
    }

    /**
     * Delete a Tile
     *
     * @param column    column
     * @param row       row
     * @param zoomLevel zoom level
     * @return number deleted, should be 0 or 1
     */
    public int deleteTile(long column, long row, long zoomLevel) {

        StringBuilder where = new StringBuilder();

        where.append(buildWhere(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel));

        where.append(" AND ");
        where.append(buildWhere(TileTable.COLUMN_TILE_COLUMN, column));

        where.append(" AND ");
        where.append(buildWhere(TileTable.COLUMN_TILE_ROW, row));

        String[] whereArgs = buildWhereArgs(new Object[]{zoomLevel, column,
                row});

        int deleted = delete(where.toString(), whereArgs);

        return deleted;
    }

    /**
     * Count of Tiles at a zoom level
     *
     * @param zoomLevel zoom level
     * @return count
     */
    public int count(long zoomLevel) {
        String where = buildWhere(TileTable.COLUMN_ZOOM_LEVEL, zoomLevel);
        String[] whereArgs = buildWhereArgs(zoomLevel);
        return count(where, whereArgs);
    }

    /**
     * Get the max length in default units that contains tiles
     *
     * @return max distance length with tiles
     * @since 1.3.0
     */
    public double getMaxLength() {
        return TileDaoUtils.getMaxLength(widths, heights);
    }

    /**
     * Get the min length in default units that contains tiles
     *
     * @return min distance length with tiles
     * @since 1.3.0
     */
    public double getMinLength() {
        return TileDaoUtils.getMinLength(widths, heights);
    }

    /**
     * Determine if the tiles are in the XYZ tile coordinate format
     *
     * @return true if XYZ tile format
     * @since 3.5.0
     */
    public boolean isXYZTiles() {

        // Convert the bounding box to wgs84
        BoundingBox boundingBox = tileMatrixSet.getBoundingBox();
        BoundingBox wgs84BoundingBox = boundingBox.transform(
                projection.getTransformation(
                        ProjectionConstants.EPSG_WORLD_GEODETIC_SYSTEM));

        boolean xyzTiles = false;

        // Verify the bounds are the entire world
        if (wgs84BoundingBox.getMinLatitude() <= ProjectionConstants.WEB_MERCATOR_MIN_LAT_RANGE
                && wgs84BoundingBox.getMaxLatitude() >= ProjectionConstants.WEB_MERCATOR_MAX_LAT_RANGE
                && wgs84BoundingBox.getMinLongitude() <= -ProjectionConstants.WGS84_HALF_WORLD_LON_WIDTH
                && wgs84BoundingBox.getMaxLongitude() >= ProjectionConstants.WGS84_HALF_WORLD_LON_WIDTH) {

            xyzTiles = true;

            // Verify each tile matrix is the correct width and height
            for (TileMatrix tileMatrix : tileMatrices) {
                long zoomLevel = tileMatrix.getZoomLevel();
                long tilesPerSide = TileBoundingBoxUtils
                        .tilesPerSide((int) zoomLevel);
                if (tileMatrix.getMatrixWidth() != tilesPerSide
                        || tileMatrix.getMatrixHeight() != tilesPerSide) {
                    xyzTiles = false;
                    break;
                }
            }
        }

        return xyzTiles;
    }

}
