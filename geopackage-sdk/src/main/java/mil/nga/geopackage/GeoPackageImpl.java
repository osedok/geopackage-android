package mil.nga.geopackage;

import android.content.Context;
import android.database.Cursor;

import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;

import java.sql.SQLException;
import java.util.List;

import mil.nga.geopackage.attributes.AttributesCursor;
import mil.nga.geopackage.attributes.AttributesDao;
import mil.nga.geopackage.attributes.AttributesTable;
import mil.nga.geopackage.attributes.AttributesTableReader;
import mil.nga.geopackage.contents.Contents;
import mil.nga.geopackage.contents.ContentsDao;
import mil.nga.geopackage.contents.ContentsDataType;
import mil.nga.geopackage.db.CoreSQLUtils;
import mil.nga.geopackage.db.GeoPackageConnection;
import mil.nga.geopackage.extension.rtree.RTreeIndexExtension;
import mil.nga.geopackage.db.GeoPackageCursorFactory;
import mil.nga.geopackage.db.GeoPackageCursorWrapper;
import mil.nga.geopackage.features.columns.GeometryColumns;
import mil.nga.geopackage.features.columns.GeometryColumnsDao;
import mil.nga.geopackage.features.index.FeatureIndexManager;
import mil.nga.geopackage.features.user.FeatureCursor;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureTable;
import mil.nga.geopackage.features.user.FeatureTableReader;
import mil.nga.geopackage.tiles.matrix.TileMatrix;
import mil.nga.geopackage.tiles.matrix.TileMatrixDao;
import mil.nga.geopackage.tiles.matrix.TileMatrixKey;
import mil.nga.geopackage.tiles.matrixset.TileMatrixSet;
import mil.nga.geopackage.tiles.matrixset.TileMatrixSetDao;
import mil.nga.geopackage.tiles.user.TileCursor;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.geopackage.tiles.user.TileTable;
import mil.nga.geopackage.tiles.user.TileTableReader;
import mil.nga.geopackage.user.custom.UserCustomCursor;
import mil.nga.geopackage.user.custom.UserCustomDao;
import mil.nga.geopackage.user.custom.UserCustomTable;
import mil.nga.geopackage.user.custom.UserCustomTableReader;
import mil.nga.sf.proj.Projection;

/**
 * A single GeoPackage database connection implementation
 *
 * @author osbornb
 */
public class GeoPackageImpl extends GeoPackageCoreImpl implements GeoPackage {

    /**
     * Context
     */
    private final Context context;

    /**
     * Database connection
     */
    private final GeoPackageConnection database;

    /**
     * Cursor factory
     */
    private final GeoPackageCursorFactory cursorFactory;

    /**
     * Constructor
     *
     * @param context       context
     * @param name          GeoPackage name
     * @param path          database path
     * @param database      database connection
     * @param cursorFactory cursor factory
     * @param writable      writable flag
     */
    GeoPackageImpl(Context context, String name, String path, GeoPackageConnection database,
                   GeoPackageCursorFactory cursorFactory, boolean writable) {
        super(name, path, database, writable);
        this.context = context;
        this.database = database;
        this.cursorFactory = cursorFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BoundingBox getFeatureBoundingBox(Projection projection,
                                             String table, boolean manual) {

        BoundingBox boundingBox = null;

        FeatureIndexManager indexManager = new FeatureIndexManager(context, this, table);
        try {
            if (manual || indexManager.isIndexed()) {
                boundingBox = indexManager.getBoundingBox(projection);
            }
        } finally {
            indexManager.close();
        }

        return boundingBox;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeoPackageCursorFactory getCursorFactory() {
        return cursorFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerCursorWrapper(String table, GeoPackageCursorWrapper cursorWrapper) {
        cursorFactory.registerTable(table,
                cursorWrapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FeatureDao getFeatureDao(GeometryColumns geometryColumns) {

        if (geometryColumns == null) {
            throw new GeoPackageException("Non null "
                    + GeometryColumns.class.getSimpleName()
                    + " is required to create "
                    + FeatureDao.class.getSimpleName());
        }

        // Read the existing table and create the dao
        FeatureTableReader tableReader = new FeatureTableReader(geometryColumns);
        final FeatureTable featureTable = tableReader.readTable(database);
        featureTable.setContents(geometryColumns.getContents());
        FeatureDao dao = new FeatureDao(getName(), database, geometryColumns, featureTable);

        // Register the table name (with and without quotes) to wrap cursors with the feature cursor
        registerCursorWrapper(geometryColumns.getTableName(),
                new GeoPackageCursorWrapper() {

                    @Override
                    public Cursor wrapCursor(Cursor cursor) {
                        return new FeatureCursor(featureTable, cursor);
                    }
                });

        // If the GeoPackage is writable and the feature table has a RTree Index
        // extension, drop the RTree triggers.  User defined functions are currently not supported.
        if (writable) {
            RTreeIndexExtension rtree = new RTreeIndexExtension(this);
            rtree.dropTriggers(featureTable);
        }

        return dao;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FeatureDao getFeatureDao(Contents contents) {

        if (contents == null) {
            throw new GeoPackageException("Non null "
                    + Contents.class.getSimpleName()
                    + " is required to create "
                    + FeatureDao.class.getSimpleName());
        }

        GeometryColumns geometryColumns = contents.getGeometryColumns();
        if (geometryColumns == null) {
            throw new GeoPackageException("No "
                    + GeometryColumns.class.getSimpleName() + " exists for "
                    + Contents.class.getSimpleName() + " " + contents.getId());
        }

        return getFeatureDao(geometryColumns);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FeatureDao getFeatureDao(FeatureTable table) {
        return getFeatureDao(table.getTableName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FeatureDao getFeatureDao(String tableName) {
        GeometryColumnsDao dao = getGeometryColumnsDao();
        List<GeometryColumns> geometryColumnsList;
        try {
            geometryColumnsList = dao.queryForEq(
                    GeometryColumns.COLUMN_TABLE_NAME, tableName);
        } catch (SQLException e) {
            throw new GeoPackageException("Failed to retrieve "
                    + FeatureDao.class.getSimpleName() + " for table name: "
                    + tableName + ". Exception retrieving "
                    + GeometryColumns.class.getSimpleName() + ".", e);
        }
        if (geometryColumnsList.isEmpty()) {
            throw new GeoPackageException(
                    "No Feature Table exists for table name: " + tableName);
        } else if (geometryColumnsList.size() > 1) {
            // This shouldn't happen with the table name unique constraint on
            // geometry columns
            throw new GeoPackageException("Unexpected state. More than one "
                    + GeometryColumns.class.getSimpleName()
                    + " matched for table name: " + tableName + ", count: "
                    + geometryColumnsList.size());
        }
        return getFeatureDao(geometryColumnsList.get(0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TileDao getTileDao(TileMatrixSet tileMatrixSet) {

        if (tileMatrixSet == null) {
            throw new GeoPackageException("Non null "
                    + TileMatrixSet.class.getSimpleName()
                    + " is required to create " + TileDao.class.getSimpleName());
        }

        // Get the Tile Matrix collection, order by zoom level ascending & pixel
        // size descending per requirement 51
        List<TileMatrix> tileMatrices;
        try {
            TileMatrixDao tileMatrixDao = getTileMatrixDao();
            QueryBuilder<TileMatrix, TileMatrixKey> qb = tileMatrixDao
                    .queryBuilder();
            qb.where().eq(TileMatrix.COLUMN_TABLE_NAME,
                    tileMatrixSet.getTableName());
            qb.orderBy(TileMatrix.COLUMN_ZOOM_LEVEL, true);
            qb.orderBy(TileMatrix.COLUMN_PIXEL_X_SIZE, false);
            qb.orderBy(TileMatrix.COLUMN_PIXEL_Y_SIZE, false);
            PreparedQuery<TileMatrix> query = qb.prepare();
            tileMatrices = tileMatrixDao.query(query);
        } catch (SQLException e) {
            throw new GeoPackageException("Failed to retrieve "
                    + TileDao.class.getSimpleName() + " for table name: "
                    + tileMatrixSet.getTableName() + ". Exception retrieving "
                    + TileMatrix.class.getSimpleName() + " collection.", e);
        }

        // Read the existing table and create the dao
        TileTableReader tableReader = new TileTableReader(
                tileMatrixSet.getTableName());
        final TileTable tileTable = tableReader.readTable(database);
        tileTable.setContents(tileMatrixSet.getContents());
        TileDao dao = new TileDao(getName(), database, tileMatrixSet, tileMatrices,
                tileTable);

        // Register the table name (with and without quotes) to wrap cursors with the tile cursor
        registerCursorWrapper(tileMatrixSet.getTableName(),
                new GeoPackageCursorWrapper() {

                    @Override
                    public Cursor wrapCursor(Cursor cursor) {
                        return new TileCursor(tileTable, cursor);
                    }
                });

        return dao;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TileDao getTileDao(Contents contents) {

        if (contents == null) {
            throw new GeoPackageException("Non null "
                    + Contents.class.getSimpleName()
                    + " is required to create " + TileDao.class.getSimpleName());
        }

        TileMatrixSet tileMatrixSet = contents.getTileMatrixSet();
        if (tileMatrixSet == null) {
            throw new GeoPackageException("No "
                    + TileMatrixSet.class.getSimpleName() + " exists for "
                    + Contents.class.getSimpleName() + " " + contents.getId());
        }

        return getTileDao(tileMatrixSet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TileDao getTileDao(TileTable table) {
        return getTileDao(table.getTableName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TileDao getTileDao(String tableName) {

        TileMatrixSetDao dao = getTileMatrixSetDao();
        List<TileMatrixSet> tileMatrixSetList;
        try {
            tileMatrixSetList = dao.queryForEq(TileMatrixSet.COLUMN_TABLE_NAME,
                    tableName);
        } catch (SQLException e) {
            throw new GeoPackageException("Failed to retrieve "
                    + TileDao.class.getSimpleName() + " for table name: "
                    + tableName + ". Exception retrieving "
                    + TileMatrixSet.class.getSimpleName() + ".", e);
        }
        if (tileMatrixSetList.isEmpty()) {
            throw new GeoPackageException(
                    "No Tile Table exists for table name: " + tableName);
        } else if (tileMatrixSetList.size() > 1) {
            // This shouldn't happen with the table name primary key on tile
            // matrix set table
            throw new GeoPackageException("Unexpected state. More than one "
                    + TileMatrixSet.class.getSimpleName()
                    + " matched for table name: " + tableName + ", count: "
                    + tileMatrixSetList.size());
        }
        return getTileDao(tileMatrixSetList.get(0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributesDao getAttributesDao(Contents contents) {

        if (contents == null) {
            throw new GeoPackageException("Non null "
                    + Contents.class.getSimpleName()
                    + " is required to create "
                    + AttributesDao.class.getSimpleName());
        }
        if (!contents.isAttributesTypeOrUnknown()) {
            throw new GeoPackageException(Contents.class.getSimpleName()
                    + " is required to be of type "
                    + ContentsDataType.ATTRIBUTES + ". Actual: "
                    + contents.getDataTypeName());
        }

        // Read the existing table and create the dao
        AttributesTableReader tableReader = new AttributesTableReader(
                contents.getTableName());
        final AttributesTable attributesTable = tableReader.readTable(database);
        attributesTable.setContents(contents);
        AttributesDao dao = new AttributesDao(getName(), database,
                attributesTable);

        // Register the table name (with and without quotes) to wrap cursors with the attributes cursor
        registerCursorWrapper(attributesTable.getTableName(),
                new GeoPackageCursorWrapper() {

                    @Override
                    public Cursor wrapCursor(Cursor cursor) {
                        return new AttributesCursor(attributesTable, cursor);
                    }
                });

        return dao;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributesDao getAttributesDao(AttributesTable table) {
        return getAttributesDao(table.getTableName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributesDao getAttributesDao(String tableName) {

        ContentsDao dao = getContentsDao();
        Contents contents = null;
        try {
            contents = dao.queryForId(tableName);
        } catch (SQLException e) {
            throw new GeoPackageException("Failed to retrieve "
                    + Contents.class.getSimpleName() + " for table name: "
                    + tableName, e);
        }
        if (contents == null) {
            throw new GeoPackageException(
                    "No Contents Table exists for table name: " + tableName);
        }
        return getAttributesDao(contents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserCustomDao getUserCustomDao(String tableName) {
        UserCustomTable table = UserCustomTableReader.readTable(database,
                tableName);
        return getUserCustomDao(table);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserCustomDao getUserCustomDao(UserCustomTable table) {
        UserCustomDao dao = new UserCustomDao(getName(), database, table);

        // Register the table name (with and without quotes) to wrap cursors with the user custom cursor
        final UserCustomTable userCustomTable = table;
        registerCursorWrapper(table.getTableName(),
                new GeoPackageCursorWrapper() {

                    @Override
                    public Cursor wrapCursor(Cursor cursor) {
                        return new UserCustomCursor(userCustomTable, cursor);
                    }
                });

        return dao;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execSQL(String sql) {
        database.execSQL(sql);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beginTransaction() {
        database.beginTransaction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endTransaction(boolean successful) {
        database.endTransaction(successful);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit() {
        database.commit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean inTransaction() {
        return database.inTransaction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cursor rawQuery(String sql, String[] args) {
        return database.rawQuery(sql, args);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GeoPackageConnection getConnection() {
        return database;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Context getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cursor foreignKeyCheck() {
        return foreignKeyCheck(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cursor foreignKeyCheck(String tableName) {
        Cursor cursor = rawQuery(CoreSQLUtils.foreignKeyCheckSQL(tableName), null);
        if (!cursor.moveToNext()) {
            cursor.close();
            cursor = null;
        }
        return cursor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cursor integrityCheck() {
        return integrityCheck(rawQuery(CoreSQLUtils.integrityCheckSQL(), null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cursor quickCheck() {
        return integrityCheck(rawQuery(CoreSQLUtils.quickCheckSQL(), null));
    }

    /**
     * Check the cursor returned from the integrity check to see if things are "ok"
     *
     * @param cursor
     * @return null if ok, else the open cursor
     */
    private Cursor integrityCheck(Cursor cursor) {
        if (cursor.moveToNext()) {
            String value = cursor.getString(0);
            if (value.equals("ok")) {
                cursor.close();
                cursor = null;
            }
        }
        return cursor;
    }

}
