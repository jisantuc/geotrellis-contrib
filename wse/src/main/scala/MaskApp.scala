package geotrellis.demo.wse

import geotrellis.geotools._
import geotrellis.vector._
import org.opengis.feature.simple.SimpleFeature
import org.geotools.data.ogr.bridj._

import scala.collection.JavaConverters._
import org.opengis.filter.sort.{SortBy, SortOrder}
import org.geotools.factory.{CommonFactoryFinder, GeoTools, Hints}
import org.opengis.filter.FilterFactory2
import geotrellis.spark.io.kryo.KryoRegistrator
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.serializer.KryoSerializer


object MaskApp {
  def main(args: Array[String]): Unit = {
    val conf = (new SparkConf()
      .setIfMissing("spark.master", "local[*]")
      .setAppName("WSE Ingest")
      .set("spark.serializer", classOf[KryoSerializer].getName)
      .set("spark.kryo.registrator", classOf[KryoRegistrator].getName))

    val sc = new SparkContext(conf)

    val rdd = sc.parallelize(Array(1,2,3,4), 4).flatMap { id =>
      val factory = new BridjOGRDataStoreFactory()
      val params = new java.util.HashMap[String, String]

      params.put("DriverName", "OpenFileGDB")
      //params.put("DatasourceName", "/Users/eugene/data/WSEL/TN_working_999.gdb")
      params.put("DatasourceName", "/vsis3/geotrellis-test/WSEL/TN/TN_working.gdb")
      val gdb = factory.createDataStore(params)
      val name = gdb.getNames().asScala.head
      val source = gdb.getFeatureSource(name)
      val features = source.getFeatures.toArray(Array.empty[SimpleFeature])
      features.map(_.toGeometry[Geometry])
    }

    println("GEOM COUNT: " + rdd.count())
  }
}
