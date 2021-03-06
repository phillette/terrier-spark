package org.terrier.spark.ml

import org.apache.spark.ml.param.ParamMap
import org.terrier.spark.eval.RankingMetrics2
import org.apache.spark.sql.Dataset
import org.apache.spark.ml.evaluation.Evaluator
import org.apache.spark.sql.Row
import org.apache.spark.ml.param.Param

object Measure extends Enumeration {
  val NDCG, MAP, P = Value
  
  def fromString(name : String) = {
    name.toLowerCase() match {
      case "ndcg" => Measure.NDCG
      case "map" => Measure.MAP
      case "ap" => Measure.MAP
      case "p" => Measure.P
    }
  }
}

class NDCGEvaluator(cutoff : Int) extends RankingEvaluator(Measure.NDCG, cutoff : Int)

/** An evaluator for multiple measures and cutoffs.
 *  NB: This may give incorrect results for recall-based measure such as MAP,
 *  as QrelTransformer does not provide the labels for documents not retrieved.
 */
class RankingEvaluator(m : Measure.Value, cutoff : Int) extends Evaluator
{
    def this(c : Int) = {
      this(Measure.NDCG, c)
    }
  
    val uid: String = "RankingEvaluator"
    
    final val rankCutoff = new Param[Int](this, "rankCutoff", "The rank cutoff")
    final val measure = new Param[Measure.Value](this, "measure", "Measure to calculate")
    
    def setMeasure(value: Measure.Value): this.type = set(measure, value)
    //def setMeasure(value: String): this.type = set(measure, Measure.fromString(value))
      
    //all ranking metrics thus far are better is bigger
    override def isLargerBetter : Boolean = true
    
    setDefault(measure, m)
    setDefault(rankCutoff, cutoff)
    
    def copy(extra: ParamMap): RankingEvaluator = {
      defaultCopy(extra)
    }
    
    def evaluateByQuery(dataset: Dataset[_]): Seq[Double] = {
      import dataset.sqlContext.implicits._
      import org.apache.spark.sql.functions._
      val xdd = dataset.coalesce(1).orderBy(asc("qid"), desc("score")).
        select("qid", "rank", "score", "label").map{
          case Row(qid: String, rank: Int, score: Double, label : Int) =>
          (qid,rank,score,label)
        }.rdd
      val xee = xdd.groupBy(_._1)
      val xff = xee.map{case (qid : String, docs : Iterable[(String,Int,Double,Int)])  =>
        (docs.map{item => item._2}.toArray, docs.map{item => (item._2 -> item._4)}.toMap)          
      }
      val xgg = xff.toLocalIterator.toSeq
      $(measure) match {
        case Measure.NDCG => new RankingMetrics2[Int](xgg).ndcgAt($(rankCutoff))
        case Measure.MAP => new RankingMetrics2[Int](xgg).averagePrecision($(rankCutoff))
        case Measure.P => new RankingMetrics2[Int](xgg).precisionAt($(rankCutoff))
      }
    }
    
    def evaluate(dataset: Dataset[_]): Double = {
      import dataset.sqlContext.implicits._
      import org.apache.spark.sql.functions._
      val xdd = dataset.coalesce(1).orderBy(asc("qid"), desc("score")).
        select("qid", "rank", "score", "label").map{
          case Row(qid: String, rank: Int, score: Double, label : Int) =>
          (qid,rank,score,label)
        }.rdd
      val xee = xdd.groupBy(_._1)
      val xff = xee.map{case (qid : String, docs : Iterable[(String,Int,Double,Int)])  =>
        (docs.map{item => item._2}.toArray, docs.map{item => (item._2 -> item._4)}.toMap)          
      }
      val xgg = xff.toLocalIterator.toSeq
      $(measure) match {
        case Measure.NDCG => new RankingMetrics2[Int](xgg).meanNDCGAt($(rankCutoff))
        case Measure.MAP => new RankingMetrics2[Int](xgg).meanAveragePrecision($(rankCutoff))
        case Measure.P => new RankingMetrics2[Int](xgg).meanPrecisionAt($(rankCutoff))
      }
      
    }
  }
