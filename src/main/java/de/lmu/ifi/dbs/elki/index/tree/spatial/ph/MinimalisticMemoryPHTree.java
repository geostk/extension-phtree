package de.lmu.ifi.dbs.elki.index.tree.spatial.ph;

import ch.ethz.globis.pht.PhTreeF;
import ch.ethz.globis.pht.v8.PhTree8;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2014
 ETH Zurich, Switzerland and Tilmann Zaeschke

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.VectorUtil.SortDBIDsBySingleDimension;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDArrayIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.KNNHeap;
import de.lmu.ifi.dbs.elki.database.ids.KNNList;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDoubleDBIDList;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.AbstractDistanceKNNQuery;
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery;
import de.lmu.ifi.dbs.elki.database.query.range.AbstractDistanceRangeQuery;
import de.lmu.ifi.dbs.elki.database.query.range.RangeQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.Norm;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.LPNormDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.SparseLPNormDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.SquaredEuclideanDistanceFunction;
import de.lmu.ifi.dbs.elki.index.AbstractIndex;
import de.lmu.ifi.dbs.elki.index.DynamicIndex;
import de.lmu.ifi.dbs.elki.index.Index;
import de.lmu.ifi.dbs.elki.index.IndexFactory;
import de.lmu.ifi.dbs.elki.index.KNNIndex;
import de.lmu.ifi.dbs.elki.index.RangeIndex;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.statistics.Counter;
import de.lmu.ifi.dbs.elki.utilities.Alias;
import de.lmu.ifi.dbs.elki.utilities.datastructures.QuickSelect;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.Parameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;

/**
 * Implementation of an in-memory PH-tree. 
 * 
 * @author Tilmann Zaeschke, Erich Schubert
 * 
 * @apiviz.has PHTreeKNNQuery
 * @apiviz.has PHTreeRangeQuery
 * 
 * @param <O> Vector type
 */
@Reference(authors = "T. Zaeschke, C. Zimmerli, M.C. Norrie", title = "The PH-Tree -- A Space-Efficient Storage Structure and Multi-Dimensional Index", booktitle = "Proc. Intl. Conf. on Management of Data (SIGMOD'14), 2014", url = "http://dx.doi.org/10.1145/361002.361007")
public class MinimalisticMemoryPHTree<O extends NumberVector> extends AbstractIndex<O> 
    implements DynamicIndex,  KNNIndex<O>, RangeIndex<O> {
  /**
   * Class logger
   */
  private static final Logging LOG = Logging.getLogger(MinimalisticMemoryPHTree.class);

  
  
  /**
   * The actual "tree" as a sorted array.
   */
  ArrayModifiableDBIDs sorted = null;
  
  private final PhTreeF<DBID> tree;

  /**
   * The number of dimensions.
   */
  private int dims = -1;

  /**
   * Counter for comparisons.
   */
  private final Counter objaccess;

  /**
   * Counter for distance computations.
   */
  private final Counter distcalc;

  /**
   * Constructor.
   * 
   * @param relation Relation to index
   */
  public MinimalisticMemoryPHTree(Relation<O> relation) {
    super(relation);
    if(LOG.isStatistics()) {
      String prefix = this.getClass().getName();
      this.objaccess = LOG.newCounter(prefix + ".objaccess");
      this.distcalc = LOG.newCounter(prefix + ".distancecalcs");
    }
    else {
      this.objaccess = null;
      this.distcalc = null;
    }
    dims = RelationUtil.dimensionality(relation);
    tree = PhTreeF.create(dims);
  }

  @Override
  public void initialize() {
    sorted = DBIDUtil.newArray(relation.getDBIDs());


    DBIDIter iter = relation.getDBIDs().iter();

    for(; iter.valid(); iter.advance()) {
      O o = relation.get(iter);
      double[] v = new double[dims];
      for (int k = 0; k < dims; k++) {
        v[k] = o.doubleValue(k);
      }
      DBID id = DBIDUtil.deref(iter);
      tree.put(v, id);
    }
  }


  @Override
  public String getLongName() {
    return "ph-tree";
  }

  @Override
  public String getShortName() {
    return "ph-tree";
  }

  @Override
  public void logStatistics() {
    if(objaccess != null) {
      LOG.statistics(objaccess);
    }
    if(distcalc != null) {
      LOG.statistics(distcalc);
    }
  }

  /**
   * Count a single object access.
   */
  protected void countObjectAccess() {
    if(objaccess != null) {
      objaccess.increment();
    }
  }

  /**
   * Count a distance computation.
   */
  protected void countDistanceComputation() {
    if(distcalc != null) {
      distcalc.increment();
    }
  }

  @Override
  public KNNQuery<O> getKNNQuery(DistanceQuery<O> distanceQuery, Object... hints) {
    DistanceFunction<? super O> df = distanceQuery.getDistanceFunction();
    // TODO: if we know this works for other distance functions, add them, too!
    if(df instanceof LPNormDistanceFunction) {
      return new PHTreeKNNQuery(distanceQuery, (Norm<? super O>) df);
    }
    if(df instanceof SquaredEuclideanDistanceFunction) {
      return new PHTreeKNNQuery(distanceQuery, (Norm<? super O>) df);
    }
    if(df instanceof SparseLPNormDistanceFunction) {
      return new PHTreeKNNQuery(distanceQuery, (Norm<? super O>) df);
    }
    return null;
  }

  @Override
  public RangeQuery<O> getRangeQuery(DistanceQuery<O> distanceQuery, Object... hints) {
    DistanceFunction<? super O> df = distanceQuery.getDistanceFunction();
    // TODO: if we know this works for other distance functions, add them, too!
    if(df instanceof LPNormDistanceFunction) {
      return new PHTreeRangeQuery(distanceQuery, (Norm<? super O>) df);
    }
    if(df instanceof SquaredEuclideanDistanceFunction) {
      return new PHTreeRangeQuery(distanceQuery, (Norm<? super O>) df);
    }
    if(df instanceof SparseLPNormDistanceFunction) {
      return new PHTreeRangeQuery(distanceQuery, (Norm<? super O>) df);
    }
    return null;
  }

  /**
   * kNN query for the ph-tree.
   * 
   * @author Tilmann Zaeschke
   */
  public class PHTreeKNNQuery extends AbstractDistanceKNNQuery<O> {
    /**
     * Norm to use.
     */
    private Norm<? super O> norm;

    /**
     * Constructor.
     * 
     * @param distanceQuery Distance query
     * @param norm Norm to use
     */
    public PHTreeKNNQuery(DistanceQuery<O> distanceQuery, Norm<? super O> norm) {
      super(distanceQuery);
      this.norm = norm;
    }

    @Override
    public KNNList getKNNForObject(O obj, int k) {
      final KNNHeap knns = DBIDUtil.newHeap(k);
      
      for (double[] v: tree.nearestNeighbour(0, oToDouble(obj, new double[dims]))) {
        DBID id = tree.get(v);
        O o2 = relation.get(id);
        double distance = norm.distance(obj, o2);
        knns.insert(distance, id);
      }
      
      return knns.toKNNList();
    }
  }

  /**
   * Range query for the ph-tree.
   */
  public class PHTreeRangeQuery extends AbstractDistanceRangeQuery<O> {
    /**
     * Norm to use.
     */
    private Norm<? super O> norm;

    /**
     * Constructor.
     * 
     * @param distanceQuery Distance query
     * @param norm Norm to use
     */
    public PHTreeRangeQuery(DistanceQuery<O> distanceQuery, Norm<? super O> norm) {
      super(distanceQuery);
      this.norm = norm;
    }

    @Override
    public void getRangeForObject(O obj, double range, ModifiableDoubleDBIDList result) {
      //TODO this is wrong
      for (double[] v: tree.nearestNeighbour(0, oToDouble(obj, new double[dims]))) {
        DBID id = tree.get(v);
        O o2 = relation.get(id);
        double distance = norm.distance(obj, o2);
        result.add(distance, id);
      }
      //((PhTree8)tree).nearestNeighbour(0, dist, dims, key);
      //kdRangeSearch(0, sorted.size(), 0, obj, result, sorted.iter(), range);
    }

    /**
     * Perform a kNN search on the kd-tree.
     * 
     * @param left Subtree begin
     * @param right Subtree end (exclusive)
     * @param axis Current splitting axis
     * @param query Query object
     * @param res kNN heap
     * @param iter Iterator variable (reduces memory footprint!)
     * @param radius Query radius
     */
    private void kdRangeSearch(int left, int right, int axis, O query, ModifiableDoubleDBIDList res, DBIDArrayIter iter, double radius) {
      // Look at current node:
      final int middle = (left + right) >>> 1;
        iter.seek(middle);
        O split = relation.get(iter);
        countObjectAccess();

        // Distance to axis:
        final double delta = split.doubleValue(axis) - query.doubleValue(axis);
        final boolean onleft = (delta >= 0);
        final boolean onright = (delta <= 0);
        final boolean close = (Math.abs(delta) <= radius);

        // Next axis:
        final int next = (axis + 1) % dims;

        // Current object:
        if(close) {
          double dist = norm.distance(query, split);
          countDistanceComputation();
          if(dist <= radius) {
            iter.seek(middle);
            res.add(dist, iter);
          }
        }
        if(left < middle && (onleft || close)) {
          kdRangeSearch(left, middle, next, query, res, iter, radius);
        }
        if(middle + 1 < right && (onright || close)) {
          kdRangeSearch(middle + 1, right, next, query, res, iter, radius);
        }
    }
  }

  /**
   * Factory class
   * 
   * @author Tilmann Zaeschke
   * 
   * @apiviz.stereotype factory
   * @apiviz.has MinimalisticMemoryPHTree
   * 
   * @param <O> Vector type
   */
  @Alias({ "miniph", "ph" })
  public static class Factory<O extends NumberVector> implements IndexFactory<O, MinimalisticMemoryPHTree<O>> {
    /**
     * Constructor. Trivial parameterizable.
     */
    public Factory() {
      super();
    }

    @Override
    public MinimalisticMemoryPHTree<O> instantiate(Relation<O> relation) {
      return new MinimalisticMemoryPHTree<>(relation);
    }

    @Override
    public TypeInformation getInputTypeRestriction() {
      return TypeUtil.NUMBER_VECTOR_FIELD;
    }
    
    public static class Parametrizer extends AbstractParameterizer {
      @Override
      protected MinimalisticMemoryPHTree.Factory<NumberVector> makeInstance() {
        return new MinimalisticMemoryPHTree.Factory<>();
      }
    }
  }

  
  @Override
  public boolean delete(DBIDRef id) {
    O o = relation.get(id);
    return tree.remove(oToDouble(o, new double[dims])) != null;
  }

  @Override
  public void insert(DBIDRef id) {
    O o = relation.get(id);
    tree.put(oToDouble(o, new double[dims]), DBIDUtil.deref(id));
  }

  @Override
  public void deleteAll(DBIDs ids) {
    DBIDIter iter = ids.iter();
    for(; iter.valid(); iter.advance()) {
      delete(iter);
    }
  }

  @Override
  public void insertAll(DBIDs ids) {
    DBIDIter iter = ids.iter();
    for(; iter.valid(); iter.advance()) {
      insert(iter);
    }
  }
  
  private double[] oToDouble(O o, double[] v) {
    for (int k = 0; k < dims; k++) {
      v[k] = o.doubleValue(k);
    }
    return v;
  }
}
