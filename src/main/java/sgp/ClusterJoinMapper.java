package sgp;
import common.geometry.TCPoint;
import org.apache.commons.math3.stat.clustering.Cluster;
import org.apache.spark.api.java.function.PairFlatMapFunction;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.List;

public class ClusterJoinMapper implements
        PairFlatMapFunction<Tuple2<Integer,Cluster>,
                Tuple2<Integer, Cluster>, Tuple2<Integer, Cluster>>
{
    private int _timeInterval; // delta-t
    private int _densityThrehold; // mu
    private double _distanceThreshold; // delta, Dist(C(ti),C(ti+1) <= delta
    private List<Tuple2<Integer, Cluster>> _broadcastClusters;

    public ClusterJoinMapper(
            List<Tuple2<Integer, Cluster>> broadcastClusters,
            int timeInterval, int densityThrehold, double distanceThreshold) {
        _broadcastClusters = broadcastClusters;
        _timeInterval = timeInterval;
        _densityThrehold = densityThrehold;
        _distanceThreshold = distanceThreshold;
    }

    @Override
    public Iterable<Tuple2<Tuple2<Integer, Cluster>, Tuple2<Integer, Cluster>>>
    call(Tuple2<Integer, Cluster> input) throws Exception {
        return apply(input);
    }

    public Iterable<Tuple2<Tuple2<Integer, Cluster>, Tuple2<Integer, Cluster>>>
    apply(Tuple2<Integer, Cluster> input) {
        List<Tuple2<Tuple2<Integer, Cluster>, Tuple2<Integer, Cluster>>> result
                = new ArrayList();

        Tuple2<Integer, Cluster> thisCluster = input;

        for (Tuple2<Integer, Cluster> t: _broadcastClusters) {

            if(getTimestampDiff(thisCluster._2(), t._2()) > _timeInterval)
                break;

            // same timestamp
            if(thisCluster._1() == t._1())
                continue;

            // density requirement
            if(validateClusterDensity(thisCluster._2()) == false ||
                    validateClusterDensity(t._2()) == false)
                continue;

            // time interval requirement
            if(validateTimeIntervals(thisCluster._2(), t._2()) == false)
                continue;

            // distance requirement
            if(validateClusterDistance(thisCluster._2(), t._2()) == false)
                continue;

            result.add(new Tuple2(thisCluster, t));
        }

        return result;
    }

    private boolean validateClusterDensity(Cluster cluster) {
        // requirement: |C(ti)| >= mu
        return cluster.getPoints().size() >= _densityThrehold;
    }

    private boolean validateTimeIntervals(Cluster c1, Cluster c2) {

        TCPoint p1 = (TCPoint) c1.getPoints().get(0);
        TCPoint p2 = (TCPoint) c2.getPoints().get(0);

        int timestamp1 = p1.getTimeStamp();
        int timestamp2 = p2.getTimeStamp();

        // validate t(i+1) > t(i)
        if(timestamp2 < timestamp1)
            return false;

        // requirement: t(i+1) - t(i) <= delta-t
        return timestamp2 - timestamp1 <= _timeInterval;
    }

    private boolean validateClusterDistance(Cluster c1, Cluster c2)
    {
        TCPoint p1 = (TCPoint)c1.getPoints().get(0);
        TCPoint p2 = (TCPoint)c2.getPoints().get(0);

        double dist = p1.distanceFrom(p2);

        return dist <= _distanceThreshold;
    }

    private int getTimestampDiff(Cluster c1, Cluster c2) {
        TCPoint p1 = (TCPoint) c1.getPoints().get(0);
        TCPoint p2 = (TCPoint) c2.getPoints().get(0);

        int timestamp1 = p1.getTimeStamp();
        int timestamp2 = p2.getTimeStamp();

        return timestamp2 - timestamp1;
    }
}
