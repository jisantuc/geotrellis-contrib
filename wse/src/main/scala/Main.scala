package geotrellis.demo.wse

import geotrellis.spark.io.kryo.{KryoRegistrator}
import geotrellis.contrib.vlm._
import geotrellis.contrib.vlm.gdal.GDALRasterSource
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.s3._
import geotrellis.spark.io.index._
import geotrellis.spark.tiling.{LayoutDefinition, FloatingLayoutScheme}
import geotrellis.util.LazyLogging

import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.rdd.RDD
import org.apache.spark.serializer.KryoSerializer

object Main extends LazyLogging {
  def main(args: Array[String]): Unit = {
    // get list of files we're going to read
    val files = Resource.readLines("wse_rasters.txt")

    val conf = (new SparkConf()
      .setIfMissing("spark.master", "local[*]")
      .setAppName("WSE Ingest")
      .set("spark.serializer", classOf[KryoSerializer].getName)
      .set("spark.kryo.registrator", classOf[KryoRegistrator].getName))
    val sc = new SparkContext(conf)

    val sourceRDD: RDD[RasterSource] =
      (sc
        .parallelize(files, files.length)
        .map(uri => new GeoTiffRasterSource(uri): RasterSource)
        //.map(uri => GDALRasterSource(uri.replaceFirst("s3://", "/vsis3/")): RasterSource)
        .cache())

    // We must iterate over our rasters to read their metadata, collecting a summary
    val summary: RasterSummary = {
      val all = RasterSummary.collect(sourceRDD)
      logger.info(s"Raster Summary: ${all.toList}")
      require(all.size == 1, "multiple CRSs detected")
      all.head // assuming we have a single one
    }

    // LayoutDefinition specifies a pixel grid as well as tile grid over a single CRS
    val layout: LayoutDefinition = {
      // LayoutScheme is something that will produce a pixel and tile grid for rasters footprints
      // Floating layout scheme will preserve the raster cell size and start tiling at the top-most, left-most pixel available
      val scheme = new FloatingLayoutScheme(512, 512)
      scheme.levelFor(summary.extent, summary.cellSize).layout
    }

    val BandRx = """.+/(\w+)_R\d+_\w+.tif""".r
    type BandName = String

    val taggedRDD: RDD[(BandName, RasterSource)] = {
      sourceRDD
        .map { rs =>
          val BandRx(bandName) = rs.uri
          (bandName, rs)
        }
    }

    val numPartitions: Int = {
      import squants.information._
      val bytes = Bytes(summary.cellType.bytes * summary.cells)
      val numPartitions: Int = (bytes / Megabytes(64)).toInt
      logger.info(s"Using $numPartitions partitions for ${bytes.toString(Gigabytes)}")
      numPartitions
    }

    val rasterRefRdd: RDD[(SpatialKey, (BandName, RasterRef))] = {
      taggedRDD
        .flatMap { case (bandName, rs) =>
          val tileSource = new LayoutTileSource(rs, layout)
          // iterate over all tile keys we can read from this RasterSource
          tileSource.keys.toIterator.map { key: SpatialKey =>
            // generate reference to a tile we can read eventually
            val ref: RasterRef = tileSource.rasterRef(key)
            (key, (bandName, ref))
          }
        }
    }

    /** Function that actually forces the reading of pixels */
    def readRefs(refs: Iterable[(BandName, RasterRef)]): MultibandTile = {
      val b = refs.toMap
      val ord = List(b("Base"), b("B1"), b("B2"), b("B3"), b("B4"), b("B5"))
      val bands = ord.flatMap(_.raster.get.tile.bands).toArray
      ArrayMultibandTile(bands)
    }

    val tileRdd: RDD[(SpatialKey, MultibandTile)] = {
      rasterRefRdd
        .groupByKey(SpatialPartitioner(numPartitions))
        .mapValues(readRefs)
    }

    // Finish assemling geotrellis raster layer type
    val layerRdd: MultibandTileLayerRDD[SpatialKey] = {
      val (tlm, zoom) = summary.toTileLayerMetadata(LocalLayout(512, 512))
      ContextRDD(tileRdd, tlm)
    }

    val writer = S3LayerWriter("geotrellis-test", "catalog")
    writer.write(LayerId("wse-test", 0), layerRdd, ZCurveKeyIndexMethod)

    sc.stop()
  }
}