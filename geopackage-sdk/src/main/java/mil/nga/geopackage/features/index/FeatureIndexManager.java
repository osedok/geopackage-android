package mil.nga.geopackage.features.index;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mil.nga.geopackage.BoundingBox;
import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageException;
import mil.nga.geopackage.db.FeatureIndexer;
import mil.nga.geopackage.extension.rtree.RTreeIndexExtension;
import mil.nga.geopackage.extension.rtree.RTreeIndexTableDao;
import mil.nga.geopackage.extension.nga.index.FeatureTableIndex;
import mil.nga.geopackage.features.user.FeatureCursor;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.features.user.ManualFeatureQuery;
import mil.nga.geopackage.io.GeoPackageProgress;
import mil.nga.sf.GeometryEnvelope;
import mil.nga.sf.proj.Projection;

/**
 * Feature Index Manager to manage indexing of feature geometries in both Android metadata and
 * within a GeoPackage using the Geometry Index Extension
 *
 * @author osbornb
 * @see mil.nga.geopackage.db.FeatureIndexer
 * @see mil.nga.geopackage.extension.nga.index.FeatureTableIndex
 * @since 1.1.0
 */
public class FeatureIndexManager {

    /**
     * Feature DAO
     */
    private final FeatureDao featureDao;

    /**
     * Feature Table Index, for indexing within a GeoPackage extension
     */
    private final FeatureTableIndex featureTableIndex;

    /**
     * Feature Indexer, for indexing within Android metadata
     */
    private final FeatureIndexer featureIndexer;

    /**
     * RTree Index Table DAO
     */
    private final RTreeIndexTableDao rTreeIndexTableDao;

    /**
     * Manual Feature Queries
     */
    private final ManualFeatureQuery manualFeatureQuery;

    /**
     * Ordered set of index locations to check in order when checking if features are indexed
     * and when querying for features
     */
    private Set<FeatureIndexType> indexLocationQueryOrder = new LinkedHashSet<>();

    /**
     * Index location, when set index calls without specifying a location go to this location
     */
    private FeatureIndexType indexLocation;

    /**
     * When an exception occurs on a certain index, continue to other index
     * types to attempt to retrieve the value
     */
    private boolean continueOnError = true;

    /**
     * Constructor
     *
     * @param context      context
     * @param geoPackage   GeoPackage
     * @param featureTable feature table
     * @since 3.1.0
     */
    public FeatureIndexManager(Context context, GeoPackage geoPackage, String featureTable) {
        this(context, geoPackage, geoPackage.getFeatureDao(featureTable));
    }

    /**
     * Constructor
     *
     * @param context    context
     * @param geoPackage GeoPackage
     * @param featureDao feature DAO
     */
    public FeatureIndexManager(Context context, GeoPackage geoPackage, FeatureDao featureDao) {
        this.featureDao = featureDao;
        featureTableIndex = new FeatureTableIndex(geoPackage, featureDao.copy());
        featureIndexer = new FeatureIndexer(context, featureDao.copy());
        RTreeIndexExtension rTreeExtension = new RTreeIndexExtension(geoPackage);
        rTreeIndexTableDao = rTreeExtension.getTableDao(featureDao.copy());
        manualFeatureQuery = new ManualFeatureQuery(featureDao.copy());

        // Set the default indexed check and query order
        indexLocationQueryOrder.add(FeatureIndexType.RTREE);
        indexLocationQueryOrder.add(FeatureIndexType.GEOPACKAGE);
        indexLocationQueryOrder.add(FeatureIndexType.METADATA);
    }

    /**
     * Close the index connections
     */
    public void close() {
        featureTableIndex.close();
        featureIndexer.close();
        // rTreeIndexTableDao.close();
    }

    /**
     * Get the feature DAO
     *
     * @return feature DAO
     * @since 1.2.5
     */
    public FeatureDao getFeatureDao() {
        return featureDao;
    }

    /**
     * Get the feature table index, used to index inside the GeoPackage as an extension
     *
     * @return feature table index
     */
    public FeatureTableIndex getFeatureTableIndex() {
        return featureTableIndex;
    }

    /**
     * Get the feature indexer, used to index in metadata tables
     *
     * @return feature indexer
     */
    public FeatureIndexer getFeatureIndexer() {
        return featureIndexer;
    }

    /**
     * Get the RTree Index Table DAO
     *
     * @return RTree index table DAO
     * @since 3.1.0
     */
    public RTreeIndexTableDao getRTreeIndexTableDao() {
        return rTreeIndexTableDao;
    }

    /**
     * Get the ordered set of ordered index query locations
     *
     * @return set of ordered index types
     * @since 3.1.0
     */
    public Set<FeatureIndexType> getIndexLocationQueryOrder() {
        return Collections.unmodifiableSet(indexLocationQueryOrder);
    }

    /**
     * Get the index location
     *
     * @return index location or null if not set
     */
    public FeatureIndexType getIndexLocation() {
        return indexLocation;
    }

    /**
     * Is the continue on error flag enabled
     *
     * @return continue on error
     * @since 3.4.0
     */
    public boolean isContinueOnError() {
        return continueOnError;
    }

    /**
     * Set the continue on error flag
     *
     * @param continueOnError continue on error
     * @since 3.4.0
     */
    public void setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }

    /**
     * Prioritize the query location order.  All types are placed at the front of the query order
     * in the order they are given. Omitting a location leaves it at it's current priority location.
     *
     * @param types feature index types
     * @since 3.1.0
     */
    public void prioritizeQueryLocation(Collection<FeatureIndexType> types) {
        prioritizeQueryLocation(types.toArray(new FeatureIndexType[types.size()]));
    }

    /**
     * Prioritize the query location order.  All types are placed at the front of the query order
     * in the order they are given. Omitting a location leaves it at it's current priority location.
     *
     * @param types feature index types
     */
    public void prioritizeQueryLocation(FeatureIndexType... types) {
        // Create a new query order set
        Set<FeatureIndexType> queryOrder = new LinkedHashSet<>();
        for (FeatureIndexType type : types) {
            if (type != FeatureIndexType.NONE) {
                queryOrder.add(type);
            }
        }
        // Add any locations not provided to this method
        queryOrder.addAll(indexLocationQueryOrder);
        // Update the query order set
        indexLocationQueryOrder = queryOrder;
    }

    /**
     * Set the index location order, overriding all previously set types
     *
     * @param types feature index types
     * @since 3.1.0
     */
    public void setIndexLocationOrder(Collection<FeatureIndexType> types) {
        setIndexLocationOrder(types.toArray(new FeatureIndexType[types.size()]));
    }

    /**
     * Set the index location order, overriding all previously set types
     *
     * @param types feature index types
     * @since 3.1.0
     */
    public void setIndexLocationOrder(FeatureIndexType... types) {
        // Create a new query order set
        Set<FeatureIndexType> queryOrder = new LinkedHashSet<>();
        for (FeatureIndexType type : types) {
            if (type != FeatureIndexType.NONE) {
                queryOrder.add(type);
            }
        }
        // Update the query order set
        indexLocationQueryOrder = queryOrder;
    }

    /**
     * Set the index location
     *
     * @param indexLocation feature index type
     */
    public void setIndexLocation(FeatureIndexType indexLocation) {
        this.indexLocation = indexLocation;
    }

    /**
     * Set the GeoPackage Progress
     *
     * @param progress GeoPackage progress
     */
    public void setProgress(GeoPackageProgress progress) {
        featureTableIndex.setProgress(progress);
        featureIndexer.setProgress(progress);
        rTreeIndexTableDao.setProgress(progress);
    }

    /**
     * Index the feature table if needed, using the set index location
     *
     * @return count
     */
    public int index() {
        return index(verifyIndexLocation(), false);
    }

    /**
     * Index the feature tables if needed for the index types
     *
     * @param types feature index types
     * @return largest count of indexed features
     * @since 2.0.0
     */
    public int index(List<FeatureIndexType> types) {
        int count = 0;
        for (FeatureIndexType type : types) {
            int typeCount = index(type);
            count = Math.max(count, typeCount);
        }
        return count;
    }

    /**
     * Index the feature table if needed
     *
     * @param type index location type
     * @return count
     */
    public int index(FeatureIndexType type) {
        return index(type, false);
    }

    /**
     * Index the feature table, using the set index location
     *
     * @param force true to force re-indexing
     * @return count
     */
    public int index(boolean force) {
        return index(verifyIndexLocation(), force);
    }

    /**
     * Index the feature tables for the index types
     *
     * @param force true to force re-indexing
     * @param types feature index types
     * @return largest count of indexed features
     * @since 2.0.0
     */
    public int index(boolean force, List<FeatureIndexType> types) {
        int count = 0;
        for (FeatureIndexType type : types) {
            int typeCount = index(type, force);
            count = Math.max(count, typeCount);
        }
        return count;
    }

    /**
     * Index the feature table
     *
     * @param type  index location type
     * @param force true to force re-indexing
     * @return count
     */
    public int index(FeatureIndexType type, boolean force) {
        if (type == null) {
            throw new GeoPackageException("FeatureIndexType is required to index");
        }
        int count = 0;
        switch (type) {
            case GEOPACKAGE:
                count = featureTableIndex.index(force);
                break;
            case METADATA:
                count = featureIndexer.index(force);
                break;
            case RTREE:
                boolean rTreeIndexed = rTreeIndexTableDao.has();
                if (!rTreeIndexed || force) {
                    if (rTreeIndexed) {
                        rTreeIndexTableDao.delete();
                    }
                    rTreeIndexTableDao.create();
                    count = rTreeIndexTableDao.count();
                }
                break;
            default:
                throw new GeoPackageException("Unsupported FeatureIndexType: "
                        + type);
        }
        return count;
    }

    /**
     * Index the feature row, using the set index location.
     * This method assumes that indexing has been completed and
     * maintained as the last indexed time is updated.
     *
     * @param row feature row to index
     * @return true if indexed
     */
    public boolean index(FeatureRow row) {
        return index(verifyIndexLocation(), row);
    }

    /**
     * Index the feature row for the index types.
     * This method assumes that indexing has been completed and
     * maintained as the last indexed time is updated.
     *
     * @param row   feature row to index
     * @param types feature index types
     * @return true if indexed from any type
     * @since 2.0.0
     */
    public boolean index(FeatureRow row, List<FeatureIndexType> types) {
        boolean indexed = false;
        for (FeatureIndexType type : types) {
            if (index(type, row)) {
                indexed = true;
            }
        }
        return indexed;
    }

    /**
     * Index the feature row. This method assumes that indexing has been completed and
     * maintained as the last indexed time is updated.
     *
     * @param type index location type
     * @param row  feature row to index
     * @return true if indexed
     */
    public boolean index(FeatureIndexType type, FeatureRow row) {
        boolean indexed = false;
        if (type == null) {
            throw new GeoPackageException("FeatureIndexType is required to index");
        }
        switch (type) {
            case GEOPACKAGE:
                indexed = featureTableIndex.index(row);
                break;
            case METADATA:
                indexed = featureIndexer.index(row);
                break;
            case RTREE:
                // Updated by triggers, ignore for RTree
                indexed = true;
                break;
            default:
                throw new GeoPackageException("Unsupported FeatureIndexType: " + type);
        }
        return indexed;
    }

    /**
     * Delete the feature index
     *
     * @return true if deleted
     */
    public boolean deleteIndex() {
        return deleteIndex(verifyIndexLocation());
    }

    /**
     * Delete the feature index from all query order locations
     *
     * @return true if deleted
     * @since 3.1.0
     */
    public boolean deleteAllIndexes() {
        return deleteIndex(indexLocationQueryOrder);
    }

    /**
     * Delete the feature index from the index types
     *
     * @param types feature index types
     * @return true if deleted from any type
     * @since 2.0.0
     */
    public boolean deleteIndex(Collection<FeatureIndexType> types) {
        boolean deleted = false;
        for (FeatureIndexType type : types) {
            if (deleteIndex(type)) {
                deleted = true;
            }
        }
        return deleted;
    }

    /**
     * Delete the feature index
     *
     * @param type feature index type
     * @return true if deleted
     */
    public boolean deleteIndex(FeatureIndexType type) {
        if (type == null) {
            throw new GeoPackageException("FeatureIndexType is required to delete index");
        }
        boolean deleted = false;
        switch (type) {
            case GEOPACKAGE:
                deleted = featureTableIndex.deleteIndex();
                break;
            case METADATA:
                deleted = featureIndexer.deleteIndex();
                break;
            case RTREE:
                rTreeIndexTableDao.delete();
                deleted = true;
                break;
            default:
                throw new GeoPackageException("Unsupported FeatureIndexType: " + type);
        }
        return deleted;
    }

    /**
     * Delete the feature index for the feature row
     *
     * @param row feature row
     * @return true if deleted
     */
    public boolean deleteIndex(FeatureRow row) {
        return deleteIndex(verifyIndexLocation(), row);
    }

    /**
     * Delete the feature index for the feature row from the index types
     *
     * @param row   feature row
     * @param types feature index types
     * @return true if deleted from any type
     * @since 2.0.0
     */
    public boolean deleteIndex(FeatureRow row, List<FeatureIndexType> types) {
        boolean deleted = false;
        for (FeatureIndexType type : types) {
            if (deleteIndex(type, row)) {
                deleted = true;
            }
        }
        return deleted;
    }

    /**
     * Delete the feature index for the feature row
     *
     * @param type feature index type
     * @param row  feature row
     * @return true if deleted
     */
    public boolean deleteIndex(FeatureIndexType type, FeatureRow row) {
        return deleteIndex(type, row.getId());
    }

    /**
     * Delete the feature index for the geometry id
     *
     * @param geomId geometry id
     * @return true if deleted
     */
    public boolean deleteIndex(long geomId) {
        return deleteIndex(verifyIndexLocation(), geomId);
    }

    /**
     * Delete the feature index for the geometry id from the index types
     *
     * @param geomId geometry id
     * @param types  feature index types
     * @return true if deleted from any type
     * @since 2.0.0
     */
    public boolean deleteIndex(long geomId, List<FeatureIndexType> types) {
        boolean deleted = false;
        for (FeatureIndexType type : types) {
            if (deleteIndex(type, geomId)) {
                deleted = true;
            }
        }
        return deleted;
    }

    /**
     * Delete the feature index for the geometry id
     *
     * @param type   feature index type
     * @param geomId geometry id
     * @return true if deleted
     */
    public boolean deleteIndex(FeatureIndexType type, long geomId) {
        if (type == null) {
            throw new GeoPackageException("FeatureIndexType is required to delete index");
        }
        boolean deleted = false;
        switch (type) {
            case GEOPACKAGE:
                deleted = featureTableIndex.deleteIndex(geomId) > 0;
                break;
            case METADATA:
                deleted = featureIndexer.deleteIndex(geomId);
                break;
            case RTREE:
                // Updated by triggers, ignore for RTree
                deleted = true;
                break;
            default:
                throw new GeoPackageException("Unsupported FeatureIndexType: " + type);
        }
        return deleted;
    }

    /**
     * Retain the feature index from the index types and delete the others
     *
     * @param type feature index type to retain
     * @return true if deleted from any type
     * @since 3.1.0
     */
    public boolean retainIndex(FeatureIndexType type) {
        List<FeatureIndexType> retain = new ArrayList<FeatureIndexType>();
        retain.add(type);
        return retainIndex(retain);
    }

    /**
     * Retain the feature index from the index types and delete the others
     *
     * @param types feature index types to retain
     * @return true if deleted from any type
     * @since 3.1.0
     */
    public boolean retainIndex(Collection<FeatureIndexType> types) {
        Set<FeatureIndexType> delete = new HashSet<>(indexLocationQueryOrder);
        delete.removeAll(types);
        return deleteIndex(delete);
    }

    /**
     * Determine if the feature table is indexed
     *
     * @return true if indexed
     */
    public boolean isIndexed() {
        boolean indexed = false;
        for (FeatureIndexType type : indexLocationQueryOrder) {
            indexed = isIndexed(type);
            if (indexed) {
                break;
            }
        }
        return indexed;
    }

    /**
     * Is the feature table indexed in the provided type location
     *
     * @param type index location type
     * @return true if indexed
     */
    public boolean isIndexed(FeatureIndexType type) {
        boolean indexed = false;
        if (type == null) {
            indexed = isIndexed();
        } else {
            switch (type) {
                case GEOPACKAGE:
                    indexed = featureTableIndex.isIndexed();
                    break;
                case METADATA:
                    indexed = featureIndexer.isIndexed();
                    break;
                case RTREE:
                    indexed = rTreeIndexTableDao.has();
                    break;
                default:
                    throw new GeoPackageException("Unsupported FeatureIndexType: " + type);
            }
        }
        return indexed;
    }

    /**
     * Get the indexed types that are currently indexed
     *
     * @return indexed types
     * @since 2.0.0
     */
    public List<FeatureIndexType> getIndexedTypes() {
        List<FeatureIndexType> indexed = new ArrayList<>();
        for (FeatureIndexType type : indexLocationQueryOrder) {
            if (isIndexed(type)) {
                indexed.add(type);
            }
        }
        return indexed;
    }

    /**
     * Get the date last indexed
     *
     * @return last indexed date or null
     */
    public Date getLastIndexed() {
        Date lastIndexed = null;
        for (FeatureIndexType type : indexLocationQueryOrder) {
            lastIndexed = getLastIndexed(type);
            if (lastIndexed != null) {
                break;
            }
        }
        return lastIndexed;
    }

    /**
     * Get the date last indexed
     *
     * @param type feature index type
     * @return last indexed date or null
     */
    public Date getLastIndexed(FeatureIndexType type) {
        Date lastIndexed = null;
        if (type == null) {
            lastIndexed = getLastIndexed();
        } else {
            switch (type) {
                case GEOPACKAGE:
                    lastIndexed = featureTableIndex.getLastIndexed();
                    break;
                case METADATA:
                    lastIndexed = featureIndexer.getLastIndexed();
                    break;
                case RTREE:
                    if (rTreeIndexTableDao.has()) {
                        // Updated by triggers, assume up to date
                        lastIndexed = new Date();
                    }
                    break;
                default:
                    throw new GeoPackageException("Unsupported FeatureIndexType: " + type);
            }
        }
        return lastIndexed;
    }

    /**
     * Query for all feature index results
     *
     * @return feature index results, close when done
     */
    public FeatureIndexResults query() {
        return query(false);
    }

    /**
     * Query for all feature index results
     *
     * @param distinct distinct rows
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct) {
        return query(distinct, featureDao.getColumnNames());
    }

    /**
     * Query for all feature index results
     *
     * @param columns columns
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns) {
        return query(false, columns);
    }

    /**
     * Query for all feature index results
     *
     * @param distinct distinct rows
     * @param columns  columns
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns) {
        FeatureIndexResults results = null;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        FeatureCursor geoPackageCursor = featureTableIndex
                                .queryFeatures(distinct, columns);
                        results = new FeatureIndexFeatureResults(
                                geoPackageCursor);
                        break;
                    case METADATA:
                        FeatureCursor geometryMetadataCursor = featureIndexer.queryFeatures(distinct, columns);
                        results = new FeatureIndexFeatureResults(geometryMetadataCursor);
                        break;
                    case RTREE:
                        FeatureCursor rTreeCursor = rTreeIndexTableDao
                                .queryFeatures(distinct, columns);
                        results = new FeatureIndexFeatureResults(rTreeCursor);
                        break;
                    default:
                        throw new GeoPackageException("Unsupported feature index type: " + type);
                }
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to query from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (results == null) {
            FeatureCursor featureCursor = manualFeatureQuery.query(distinct, columns);
            results = new FeatureIndexFeatureResults(featureCursor);
        }
        return results;
    }

    /**
     * Query for all feature index count
     *
     * @param column count column name
     * @return count
     * @since 4.0.0
     */
    public long countColumn(String column) {
        return count(false, column);
    }

    /**
     * Query for all feature index count
     *
     * @param distinct distinct column values
     * @param column   count column name
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column) {
        Long count = null;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        count = (long) featureTableIndex.countFeatures(distinct,
                                column);
                        break;
                    case METADATA:
                        count = (long) featureIndexer.countFeatures(distinct,
                                column);
                        break;
                    case RTREE:
                        count = (long) rTreeIndexTableDao.countFeatures(distinct,
                                column);
                        break;
                    default:
                        throw new GeoPackageException(
                                "Unsupported feature index type: " + type);
                }
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to count from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (count == null) {
            count = (long) manualFeatureQuery.count(distinct, column);
        }
        return count;
    }

    /**
     * Query for all feature index count
     *
     * @return count
     */
    public long count() {
        Long count = null;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        count = featureTableIndex.count();
                        break;
                    case METADATA:
                        count = featureIndexer.count();
                        break;
                    case RTREE:
                        count = (long) rTreeIndexTableDao.count();
                        break;
                    default:
                        throw new GeoPackageException("Unsupported feature index type: " + type);
                }
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to count from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (count == null) {
            count = (long) manualFeatureQuery.countWithGeometries();
        }
        return count;
    }

    /**
     * Query for feature index results
     *
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(Map<String, Object> fieldValues) {
        return query(false, fieldValues);
    }

    /**
     * Query for feature index results
     *
     * @param distinct    distinct rows
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct,
                                     Map<String, Object> fieldValues) {
        String where = featureDao.buildWhere(fieldValues.entrySet());
        String[] whereArgs = featureDao.buildWhereArgs(fieldValues.values());
        return query(distinct, where, whereArgs);
    }

    /**
     * Query for feature index results
     *
     * @param columns     columns
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns,
                                     Map<String, Object> fieldValues) {
        return query(false, columns, fieldValues);
    }

    /**
     * Query for feature index results
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     Map<String, Object> fieldValues) {
        String where = featureDao.buildWhere(fieldValues.entrySet());
        String[] whereArgs = featureDao.buildWhereArgs(fieldValues.values());
        return query(distinct, columns, where, whereArgs);
    }

    /**
     * Query for feature index count
     *
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public long count(Map<String, Object> fieldValues) {
        return count(false, null, fieldValues);
    }

    /**
     * Query for feature index count
     *
     * @param column      count column name
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public long count(String column, Map<String, Object> fieldValues) {
        return count(false, column, fieldValues);
    }

    /**
     * Query for feature index count
     *
     * @param distinct    distinct column values
     * @param column      count column name
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public long count(boolean distinct, String column,
                      Map<String, Object> fieldValues) {
        String where = featureDao.buildWhere(fieldValues.entrySet());
        String[] whereArgs = featureDao.buildWhereArgs(fieldValues.values());
        return count(distinct, column, where, whereArgs);
    }

    /**
     * Query for feature index results
     *
     * @param where where clause
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(String where) {
        return query(false, where);
    }

    /**
     * Query for feature index results
     *
     * @param distinct distinct rows
     * @param where    where clause
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String where) {
        return query(distinct, where, null);
    }

    /**
     * Query for feature index results
     *
     * @param columns columns
     * @param where   where clause
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, String where) {
        return query(false, columns, where);
    }

    /**
     * Query for feature index results
     *
     * @param distinct distinct rows
     * @param columns  columns
     * @param where    where clause
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     String where) {
        return query(distinct, columns, where, null);
    }

    /**
     * Query for feature index count
     *
     * @param where where clause
     * @return count
     * @since 3.4.0
     */
    public long count(String where) {
        return count(false, null, where);
    }

    /**
     * Query for feature index count
     *
     * @param column count column name
     * @param where  where clause
     * @return count
     * @since 4.0.0
     */
    public long count(String column, String where) {
        return count(false, column, where);
    }

    /**
     * Query for feature index count
     *
     * @param distinct distinct column values
     * @param column   count column name
     * @param where    where clause
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, String where) {
        return count(distinct, column, where, null);
    }

    /**
     * Query for feature index results
     *
     * @param where     where clause
     * @param whereArgs where arguments
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(String where, String[] whereArgs) {
        return query(false, where, whereArgs);
    }

    /**
     * Query for feature index results
     *
     * @param distinct  distinct rows
     * @param where     where clause
     * @param whereArgs where arguments
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String where,
                                     String[] whereArgs) {
        return query(distinct, featureDao.getColumnNames(), where, whereArgs);
    }

    /**
     * Query for feature index results
     *
     * @param columns   columns
     * @param where     where clause
     * @param whereArgs where arguments
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, String where,
                                     String[] whereArgs) {
        return query(false, columns, where, whereArgs);
    }

    /**
     * Query for feature index results
     *
     * @param distinct  distinct rows
     * @param columns   columns
     * @param where     where clause
     * @param whereArgs where arguments
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     String where, String[] whereArgs) {
        FeatureIndexResults results = null;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        FeatureCursor geoPackageCursor = featureTableIndex
                                .queryFeatures(distinct, columns, where, whereArgs);
                        results = new FeatureIndexFeatureResults(
                                geoPackageCursor);
                        break;
                    case METADATA:
                        FeatureCursor geometryMetadataCursor = featureIndexer.queryFeatures(distinct, columns, where, whereArgs);
                        results = new FeatureIndexFeatureResults(geometryMetadataCursor);
                        break;
                    case RTREE:
                        FeatureCursor rTreeCursor = rTreeIndexTableDao
                                .queryFeatures(distinct, columns, where, whereArgs);
                        results = new FeatureIndexFeatureResults(rTreeCursor);
                        break;
                    default:
                        throw new GeoPackageException(
                                "Unsupported feature index type: " + type);
                }
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to query from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (results == null) {
            FeatureCursor featureCursor = manualFeatureQuery.query(distinct, columns, where,
                    whereArgs);
            results = new FeatureIndexFeatureResults(featureCursor);
        }
        return results;
    }

    /**
     * Query for feature index count
     *
     * @param where     where clause
     * @param whereArgs where arguments
     * @return count
     * @since 3.4.0
     */
    public long count(String where, String[] whereArgs) {
        return count(false, null, where, whereArgs);
    }

    /**
     * Query for feature index count
     *
     * @param column    count column name
     * @param where     where clause
     * @param whereArgs where arguments
     * @return count
     * @since 4.0.0
     */
    public long count(String column, String where, String[] whereArgs) {
        return count(false, column, where, whereArgs);
    }

    /**
     * Query for feature index count
     *
     * @param distinct  distinct column values
     * @param column    count column name
     * @param where     where clause
     * @param whereArgs where arguments
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, String where,
                      String[] whereArgs) {
        Long count = null;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        count = (long) featureTableIndex.countFeatures(distinct, column, where,
                                whereArgs);
                        break;
                    case METADATA:
                        count = (long) featureIndexer.countFeatures(distinct, column, where,
                                whereArgs);
                        break;
                    case RTREE:
                        count = (long) rTreeIndexTableDao.countFeatures(distinct, column, where,
                                whereArgs);
                        break;
                    default:
                        throw new GeoPackageException(
                                "Unsupported feature index type: " + type);
                }
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to count from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (count == null) {
            count = (long) manualFeatureQuery.count(distinct, column, where, whereArgs);
        }
        return count;
    }

    /**
     * Query for the feature index bounds
     *
     * @return bounding box
     */
    public BoundingBox getBoundingBox() {
        BoundingBox bounds = null;
        boolean success = false;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        bounds = featureTableIndex.getBoundingBox();
                        break;
                    case METADATA:
                        bounds = featureIndexer.getBoundingBox();
                        break;
                    case RTREE:
                        bounds = rTreeIndexTableDao.getBoundingBox();
                        break;
                    default:
                        throw new GeoPackageException("Unsupported feature index type: " + type);
                }
                success = true;
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to get bounding box from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (!success) {
            bounds = manualFeatureQuery.getBoundingBox();
        }
        return bounds;
    }

    /**
     * Query for the feature index bounds and return in the provided projection
     *
     * @param projection desired projection
     * @return bounding box
     */
    public BoundingBox getBoundingBox(Projection projection) {
        BoundingBox bounds = null;
        boolean success = false;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        bounds = featureTableIndex.getBoundingBox(projection);
                        break;
                    case METADATA:
                        bounds = featureIndexer.getBoundingBox(projection);
                        break;
                    case RTREE:
                        bounds = rTreeIndexTableDao.getBoundingBox(projection);
                        break;
                    default:
                        throw new GeoPackageException("Unsupported feature index type: " + type);
                }
                success = true;
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to get bounding box from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (!success) {
            bounds = manualFeatureQuery.getBoundingBox(projection);
        }
        return bounds;
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param boundingBox bounding box
     * @return feature index results, close when done
     */
    public FeatureIndexResults query(BoundingBox boundingBox) {
        return query(false, boundingBox);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct rows
     * @param boundingBox bounding box
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct,
                                     BoundingBox boundingBox) {
        return query(distinct, boundingBox.buildEnvelope());
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param columns     columns
     * @param boundingBox bounding box
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns,
                                     BoundingBox boundingBox) {
        return query(false, columns, boundingBox);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param boundingBox bounding box
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     BoundingBox boundingBox) {
        return query(distinct, columns, boundingBox.buildEnvelope());
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param boundingBox bounding box
     * @return count
     */
    public long count(BoundingBox boundingBox) {
        return count(false, null, boundingBox);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param column      count column name
     * @param boundingBox bounding box
     * @return count
     * @since 4.0.0
     */
    public long count(String column, BoundingBox boundingBox) {
        return count(false, column, boundingBox);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct column values
     * @param column      count column name
     * @param boundingBox bounding box
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column,
                      BoundingBox boundingBox) {
        return count(distinct, column, boundingBox.buildEnvelope());
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param boundingBox bounding box
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(BoundingBox boundingBox,
                                     Map<String, Object> fieldValues) {
        return query(false, boundingBox, fieldValues);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct rows
     * @param boundingBox bounding box
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, BoundingBox boundingBox,
                                     Map<String, Object> fieldValues) {
        return query(distinct, boundingBox.buildEnvelope(), fieldValues);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param columns     columns
     * @param boundingBox bounding box
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, BoundingBox boundingBox,
                                     Map<String, Object> fieldValues) {
        return query(false, columns, boundingBox, fieldValues);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param boundingBox bounding box
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     BoundingBox boundingBox, Map<String, Object> fieldValues) {
        return query(distinct, columns, boundingBox.buildEnvelope(),
                fieldValues);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param boundingBox bounding box
     * @param fieldValues field values
     * @return count
     * @since 3.4.0
     */
    public long count(BoundingBox boundingBox,
                      Map<String, Object> fieldValues) {
        return count(false, null, boundingBox, fieldValues);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param column      column name
     * @param boundingBox bounding box
     * @param fieldValues field values
     * @return count
     * @since 4.0.0
     */
    public long count(String column, BoundingBox boundingBox,
                      Map<String, Object> fieldValues) {
        return count(false, column, boundingBox, fieldValues);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct column values
     * @param column      column name
     * @param boundingBox bounding box
     * @param fieldValues field values
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, BoundingBox boundingBox,
                      Map<String, Object> fieldValues) {
        return count(distinct, column, boundingBox.buildEnvelope(),
                fieldValues);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param boundingBox bounding box
     * @param where       where clause
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(BoundingBox boundingBox, String where) {
        return query(false, boundingBox, where);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct rows
     * @param boundingBox bounding box
     * @param where       where clause
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, BoundingBox boundingBox,
                                     String where) {
        return query(distinct, boundingBox, where, null);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param columns     columns
     * @param boundingBox bounding box
     * @param where       where clause
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, BoundingBox boundingBox,
                                     String where) {
        return query(false, columns, boundingBox, where);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param boundingBox bounding box
     * @param where       where clause
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     BoundingBox boundingBox, String where) {
        return query(distinct, columns, boundingBox, where, null);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param boundingBox bounding box
     * @param where       where clause
     * @return count
     * @since 3.4.0
     */
    public long count(BoundingBox boundingBox, String where) {
        return count(false, null, boundingBox, where);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param column      column name
     * @param boundingBox bounding box
     * @param where       where clause
     * @return count
     * @since 4.0.0
     */
    public long count(String column, BoundingBox boundingBox, String where) {
        return count(false, column, boundingBox, where);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct column values
     * @param column      column name
     * @param boundingBox bounding box
     * @param where       where clause
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, BoundingBox boundingBox,
                      String where) {
        return count(distinct, column, boundingBox, where, null);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param boundingBox bounding box
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(BoundingBox boundingBox, String where,
                                     String[] whereArgs) {
        return query(false, boundingBox, where, whereArgs);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct rows
     * @param boundingBox bounding box
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, BoundingBox boundingBox,
                                     String where, String[] whereArgs) {
        return query(distinct, boundingBox.buildEnvelope(), where, whereArgs);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param columns     columns
     * @param boundingBox bounding box
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, BoundingBox boundingBox,
                                     String where, String[] whereArgs) {
        return query(false, columns, boundingBox, where, whereArgs);
    }

    /**
     * Query for feature index results within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param boundingBox bounding box
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     BoundingBox boundingBox, String where, String[] whereArgs) {
        return query(distinct, columns, boundingBox.buildEnvelope(), where,
                whereArgs);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param boundingBox bounding box
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return count
     * @since 3.4.0
     */
    public long count(BoundingBox boundingBox, String where,
                      String[] whereArgs) {
        return count(false, null, boundingBox, where, whereArgs);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param column      count column value
     * @param boundingBox bounding box
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return count
     * @since 4.0.0
     */
    public long count(String column, BoundingBox boundingBox, String where,
                      String[] whereArgs) {
        return count(false, column, boundingBox, where, whereArgs);
    }

    /**
     * Query for feature index count within the bounding box, projected
     * correctly
     *
     * @param distinct    distinct column values
     * @param column      count column value
     * @param boundingBox bounding box
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, BoundingBox boundingBox,
                      String where, String[] whereArgs) {
        return count(distinct, column, boundingBox.buildEnvelope(), where,
                whereArgs);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param envelope geometry envelope
     * @return feature index results, close when done
     */
    public FeatureIndexResults query(GeometryEnvelope envelope) {
        return query(false, envelope);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param distinct distinct rows
     * @param envelope geometry envelope
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct,
                                     GeometryEnvelope envelope) {
        return query(distinct, envelope, null, null);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param columns  columns
     * @param envelope geometry envelope
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns,
                                     GeometryEnvelope envelope) {
        return query(false, columns, envelope);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param distinct distinct rows
     * @param columns  columns
     * @param envelope geometry envelope
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     GeometryEnvelope envelope) {
        return query(distinct, columns, envelope, null, null);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param envelope geometry envelope
     * @return count
     */
    public long count(GeometryEnvelope envelope) {
        return count(false, null, envelope);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param column   count column name
     * @param envelope geometry envelope
     * @return count
     * @since 4.0.0
     */
    public long count(String column, GeometryEnvelope envelope) {
        return count(false, column, envelope);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param distinct distinct column values
     * @param column   count column name
     * @param envelope geometry envelope
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column,
                      GeometryEnvelope envelope) {
        Long count = null;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        if (column != null) {
                            count = (long) featureTableIndex.countFeatures(distinct,
                                    column, envelope);
                        } else {
                            count = featureTableIndex.count(envelope);
                        }
                        break;
                    case METADATA:
                        if (column != null) {
                            count = (long) featureIndexer.countFeatures(distinct,
                                    column, envelope);
                        } else {
                            count = featureIndexer.count(envelope);
                        }
                        break;
                    case RTREE:
                        if (column != null) {
                            count = (long) rTreeIndexTableDao.count(distinct,
                                    column, envelope);
                        } else {
                            count = (long) rTreeIndexTableDao.count(envelope);
                        }
                        break;
                    default:
                        throw new GeoPackageException("Unsupported feature index type: " + type);
                }
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to count from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (count == null) {
            if (column != null) {
                throw new GeoPackageException(
                        "Count by column and envelope is unsupported as a manual feature query. column: "
                                + column);
            } else {
                count = manualFeatureQuery.count(envelope);
            }
        }
        return count;
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param envelope    geometry envelope
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(GeometryEnvelope envelope,
                                     Map<String, Object> fieldValues) {
        return query(false, envelope, fieldValues);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param distinct    distinct rows
     * @param envelope    geometry envelope
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct,
                                     GeometryEnvelope envelope, Map<String, Object> fieldValues) {
        String where = featureDao.buildWhere(fieldValues.entrySet());
        String[] whereArgs = featureDao.buildWhereArgs(fieldValues.values());
        return query(distinct, envelope, where, whereArgs);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param columns     columns
     * @param envelope    geometry envelope
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns,
                                     GeometryEnvelope envelope, Map<String, Object> fieldValues) {
        return query(false, columns, envelope, fieldValues);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param envelope    geometry envelope
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     GeometryEnvelope envelope, Map<String, Object> fieldValues) {
        String where = featureDao.buildWhere(fieldValues.entrySet());
        String[] whereArgs = featureDao.buildWhereArgs(fieldValues.values());
        return query(distinct, columns, envelope, where, whereArgs);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param envelope    geometry envelope
     * @param fieldValues field values
     * @return count
     * @since 3.4.0
     */
    public long count(GeometryEnvelope envelope,
                      Map<String, Object> fieldValues) {
        return count(false, null, envelope, fieldValues);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param column      count column name
     * @param envelope    geometry envelope
     * @param fieldValues field values
     * @return count
     * @since 4.0.0
     */
    public long count(String column, GeometryEnvelope envelope,
                      Map<String, Object> fieldValues) {
        return count(false, column, envelope, fieldValues);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param distinct    distinct column values
     * @param column      count column name
     * @param envelope    geometry envelope
     * @param fieldValues field values
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column,
                      GeometryEnvelope envelope, Map<String, Object> fieldValues) {
        String where = featureDao.buildWhere(fieldValues.entrySet());
        String[] whereArgs = featureDao.buildWhereArgs(fieldValues.values());
        return count(distinct, column, envelope, where, whereArgs);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param envelope geometry envelope
     * @param where    where clause
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(GeometryEnvelope envelope, String where) {
        return query(false, envelope, where);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param distinct distinct rows
     * @param envelope geometry envelope
     * @param where    where clause
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct,
                                     GeometryEnvelope envelope, String where) {
        return query(distinct, envelope, where, null);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param columns  columns
     * @param envelope geometry envelope
     * @param where    where clause
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns,
                                     GeometryEnvelope envelope, String where) {
        return query(false, columns, envelope, where);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param distinct distinct rows
     * @param columns  columns
     * @param envelope geometry envelope
     * @param where    where clause
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     GeometryEnvelope envelope, String where) {
        return query(distinct, columns, envelope, where, null);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param envelope geometry envelope
     * @param where    where clause
     * @return count
     * @since 3.4.0
     */
    public long count(GeometryEnvelope envelope, String where) {
        return count(false, null, envelope, where);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param column   count column name
     * @param envelope geometry envelope
     * @param where    where clause
     * @return count
     * @since 4.0.0
     */
    public long count(String column, GeometryEnvelope envelope, String where) {
        return count(false, column, envelope, where);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param distinct distinct column values
     * @param column   count column name
     * @param envelope geometry envelope
     * @param where    where clause
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column,
                      GeometryEnvelope envelope, String where) {
        return count(distinct, column, envelope, where, null);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param envelope  geometry envelope
     * @param where     where clause
     * @param whereArgs where arguments
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(GeometryEnvelope envelope, String where,
                                     String[] whereArgs) {
        return query(false, envelope, where, whereArgs);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param distinct  distinct rows
     * @param envelope  geometry envelope
     * @param where     where clause
     * @param whereArgs where arguments
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct,
                                     GeometryEnvelope envelope, String where, String[] whereArgs) {
        return query(distinct, featureDao.getColumnNames(), envelope, where,
                whereArgs);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param columns   columns
     * @param envelope  geometry envelope
     * @param where     where clause
     * @param whereArgs where arguments
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns,
                                     GeometryEnvelope envelope, String where, String[] whereArgs) {
        return query(false, columns, envelope, where, whereArgs);
    }

    /**
     * Query for feature index results within the Geometry Envelope
     *
     * @param distinct  distinct rows
     * @param columns   columns
     * @param envelope  geometry envelope
     * @param where     where clause
     * @param whereArgs where arguments
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     GeometryEnvelope envelope, String where, String[] whereArgs) {
        FeatureIndexResults results = null;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        FeatureCursor geoPackageCursor = featureTableIndex
                                .queryFeatures(distinct, columns, envelope, where, whereArgs);
                        results = new FeatureIndexFeatureResults(
                                geoPackageCursor);
                        break;
                    case METADATA:
                        FeatureCursor geometryMetadataCursor = featureIndexer.queryFeatures(distinct, columns, envelope, where, whereArgs);
                        results = new FeatureIndexFeatureResults(geometryMetadataCursor);
                        break;
                    case RTREE:
                        FeatureCursor rTreeCursor = rTreeIndexTableDao
                                .queryFeatures(distinct, columns, envelope, where, whereArgs);
                        results = new FeatureIndexFeatureResults(rTreeCursor);
                        break;
                    default:
                        throw new GeoPackageException(
                                "Unsupported feature index type: " + type);
                }
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to query from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (results == null) {
            results = manualFeatureQuery.query(distinct, columns, envelope, where, whereArgs);
        }
        return results;
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param envelope  geometry envelope
     * @param where     where clause
     * @param whereArgs where arguments
     * @return count
     * @since 3.4.0
     */
    public long count(GeometryEnvelope envelope, String where,
                      String[] whereArgs) {
        return count(false, null, envelope, where, whereArgs);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param column    count column name
     * @param envelope  geometry envelope
     * @param where     where clause
     * @param whereArgs where arguments
     * @return count
     * @since 4.0.0
     */
    public long count(String column, GeometryEnvelope envelope, String where,
                      String[] whereArgs) {
        return count(false, column, envelope, where, whereArgs);
    }

    /**
     * Query for feature index count within the Geometry Envelope
     *
     * @param distinct  distinct column values
     * @param column    count column name
     * @param envelope  geometry envelope
     * @param where     where clause
     * @param whereArgs where arguments
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column,
                      GeometryEnvelope envelope, String where, String[] whereArgs) {
        Long count = null;
        for (FeatureIndexType type : getLocation()) {
            try {
                switch (type) {
                    case GEOPACKAGE:
                        count = (long) featureTableIndex.countFeatures(distinct, column, envelope,
                                where, whereArgs);
                        break;
                    case METADATA:
                        count = (long) featureIndexer.countFeatures(distinct, column, envelope,
                                where, whereArgs);
                        break;
                    case RTREE:
                        count = (long) rTreeIndexTableDao.countFeatures(distinct, column, envelope,
                                where, whereArgs);
                        break;
                    default:
                        throw new GeoPackageException(
                                "Unsupported feature index type: " + type);
                }
                break;
            } catch (Exception e) {
                if (continueOnError) {
                    Log.e(FeatureIndexManager.class.getSimpleName(), "Failed to count from feature index: " + type, e);
                } else {
                    throw e;
                }
            }
        }
        if (count == null) {
            if (column != null) {
                throw new GeoPackageException(
                        "Count by column and envelope is unsupported as a manual feature query. column: "
                                + column);
            } else {
                count = manualFeatureQuery.count(envelope, where, whereArgs);
            }
        }
        return count;
    }

    /**
     * Query for feature index results within the bounding box in
     * the provided projection
     *
     * @param boundingBox bounding box
     * @param projection  projection
     * @return feature index results, close when done
     */
    public FeatureIndexResults query(BoundingBox boundingBox, Projection projection) {
        return query(false, boundingBox, projection);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct rows
     * @param boundingBox bounding box
     * @param projection  projection
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, BoundingBox boundingBox,
                                     Projection projection) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return query(distinct, featureBoundingBox);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param columns     columns
     * @param boundingBox bounding box
     * @param projection  projection
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, BoundingBox boundingBox,
                                     Projection projection) {
        return query(false, columns, boundingBox, projection);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param boundingBox bounding box
     * @param projection  projection
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     BoundingBox boundingBox, Projection projection) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return query(distinct, columns, featureBoundingBox);
    }

    /**
     * Query for feature index count within the bounding box in
     * the provided projection
     *
     * @param boundingBox bounding box
     * @param projection  projection
     * @return count
     */
    public long count(BoundingBox boundingBox, Projection projection) {
        return count(false, null, boundingBox, projection);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param column      count column name
     * @param boundingBox bounding box
     * @param projection  projection
     * @return count
     * @since 4.0.0
     */
    public long count(String column, BoundingBox boundingBox,
                      Projection projection) {
        return count(false, column, boundingBox, projection);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct column values
     * @param column      count column name
     * @param boundingBox bounding box
     * @param projection  projection
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, BoundingBox boundingBox,
                      Projection projection) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return count(distinct, column, featureBoundingBox);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param boundingBox bounding box
     * @param projection  projection
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(BoundingBox boundingBox,
                                     Projection projection, Map<String, Object> fieldValues) {
        return query(false, boundingBox, projection, fieldValues);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct rows
     * @param boundingBox bounding box
     * @param projection  projection
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, BoundingBox boundingBox,
                                     Projection projection, Map<String, Object> fieldValues) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return query(distinct, featureBoundingBox, fieldValues);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param columns     columns
     * @param boundingBox bounding box
     * @param projection  projection
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, BoundingBox boundingBox,
                                     Projection projection, Map<String, Object> fieldValues) {
        return query(false, columns, boundingBox, projection, fieldValues);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param boundingBox bounding box
     * @param projection  projection
     * @param fieldValues field values
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     BoundingBox boundingBox, Projection projection,
                                     Map<String, Object> fieldValues) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return query(distinct, columns, featureBoundingBox, fieldValues);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param boundingBox bounding box
     * @param projection  projection
     * @param fieldValues field values
     * @return count
     * @since 3.4.0
     */
    public long count(BoundingBox boundingBox, Projection projection,
                      Map<String, Object> fieldValues) {
        return count(false, null, boundingBox, projection, fieldValues);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param column      count column value
     * @param boundingBox bounding box
     * @param projection  projection
     * @param fieldValues field values
     * @return count
     * @since 4.0.0
     */
    public long count(String column, BoundingBox boundingBox,
                      Projection projection, Map<String, Object> fieldValues) {
        return count(false, column, boundingBox, projection, fieldValues);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct column values
     * @param column      count column value
     * @param boundingBox bounding box
     * @param projection  projection
     * @param fieldValues field values
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, BoundingBox boundingBox,
                      Projection projection, Map<String, Object> fieldValues) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return count(distinct, column, featureBoundingBox, fieldValues);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(BoundingBox boundingBox,
                                     Projection projection, String where) {
        return query(false, boundingBox, projection, where);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct rows
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, BoundingBox boundingBox,
                                     Projection projection, String where) {
        return query(distinct, boundingBox, projection, where, null);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param columns     columns
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, BoundingBox boundingBox,
                                     Projection projection, String where) {
        return query(false, columns, boundingBox, projection, where);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     BoundingBox boundingBox, Projection projection, String where) {
        return query(distinct, columns, boundingBox, projection, where, null);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @return count
     * @since 3.4.0
     */
    public long count(BoundingBox boundingBox, Projection projection,
                      String where) {
        return count(false, null, boundingBox, projection, where);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param column      count column name
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @return count
     * @since 4.0.0
     */
    public long count(String column, BoundingBox boundingBox,
                      Projection projection, String where) {
        return count(false, column, boundingBox, projection, where);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct column values
     * @param column      count column name
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, BoundingBox boundingBox,
                      Projection projection, String where) {
        return count(distinct, column, boundingBox, projection, where, null);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return feature index results, close when done
     * @since 3.4.0
     */
    public FeatureIndexResults query(BoundingBox boundingBox,
                                     Projection projection, String where, String[] whereArgs) {
        return query(false, boundingBox, projection, where, whereArgs);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct rows
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, BoundingBox boundingBox,
                                     Projection projection, String where, String[] whereArgs) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return query(distinct, featureBoundingBox, where, whereArgs);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param columns     columns
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return feature index results, close when done
     * @since 3.5.0
     */
    public FeatureIndexResults query(String[] columns, BoundingBox boundingBox,
                                     Projection projection, String where, String[] whereArgs) {
        return query(false, columns, boundingBox, projection, where, whereArgs);
    }

    /**
     * Query for feature index results within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct rows
     * @param columns     columns
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return feature index results, close when done
     * @since 4.0.0
     */
    public FeatureIndexResults query(boolean distinct, String[] columns,
                                     BoundingBox boundingBox, Projection projection, String where,
                                     String[] whereArgs) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return query(distinct, columns, featureBoundingBox, where, whereArgs);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return count
     * @since 3.4.0
     */
    public long count(BoundingBox boundingBox, Projection projection,
                      String where, String[] whereArgs) {
        return count(false, null, boundingBox, projection, where, whereArgs);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param column      count column name
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return count
     * @since 4.0.0
     */
    public long count(String column, BoundingBox boundingBox,
                      Projection projection, String where, String[] whereArgs) {
        return count(false, column, boundingBox, projection, where, whereArgs);
    }

    /**
     * Query for feature index count within the bounding box in the provided
     * projection
     *
     * @param distinct    distinct column values
     * @param column      count column name
     * @param boundingBox bounding box
     * @param projection  projection
     * @param where       where clause
     * @param whereArgs   where arguments
     * @return count
     * @since 4.0.0
     */
    public long count(boolean distinct, String column, BoundingBox boundingBox,
                      Projection projection, String where, String[] whereArgs) {
        BoundingBox featureBoundingBox = featureDao
                .projectBoundingBox(boundingBox, projection);
        return count(distinct, column, featureBoundingBox, where, whereArgs);
    }

    /**
     * Get a feature index location to iterate over indexed types
     *
     * @return feature index location
     * @since 3.4.0
     */
    public FeatureIndexLocation getLocation() {
        return new FeatureIndexLocation(this);
    }

    /**
     * Get the first ordered indexed type
     *
     * @return feature index type
     * @since 3.4.0
     */
    public FeatureIndexType getIndexedType() {

        FeatureIndexType indexType = FeatureIndexType.NONE;

        // Check for an indexed type
        for (FeatureIndexType type : indexLocationQueryOrder) {
            if (isIndexed(type)) {
                indexType = type;
                break;
            }
        }

        return indexType;
    }

    /**
     * Verify the index location is set
     *
     * @return feature index type
     */
    private FeatureIndexType verifyIndexLocation() {
        if (indexLocation == null) {
            throw new GeoPackageException("Index Location is not set, set the location or call an index method specifying the location");
        }
        return indexLocation;
    }

}
