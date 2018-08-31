package geotrellis.contrib.vlm


import geotrellis.raster._
import geotrellis.raster.reproject.Reproject
import geotrellis.vector._
import geotrellis.proj4._
import geotrellis.spark._
import geotrellis.spark.io.hadoop._
import geotrellis.spark.tiling._
import geotrellis.spark.testkit._

import org.scalatest._

import org.apache.spark._

class RasterSourceRDDSpec extends FunSpec with TestEnvironment {
  val uri = "file:///tmp/aspect-tiled.tif"
  val rasterSource = new GeoTiffRasterSource(uri)

  val targetCRS = CRS.fromEpsgCode(3857)
  val scheme = ZoomedLayoutScheme(targetCRS)
  val layout = scheme.levelForZoom(13).layout

  describe("reading in GeoTiffs as RDDs") {
    it("should have the right number of tiles") {
      val expectedKeys =
        layout
          .mapTransform
          .keysForGeometry(rasterSource.extent.toPolygon)
          .toSeq
          .sortBy { key => (key.col, key.row) }

      val rdd = RasterSourceRDD(rasterSource, layout)

      val actualKeys = rdd.keys.collect().sortBy { key => (key.col, key.row) }

      for ((actual, expected) <- actualKeys.zip(expectedKeys)) {
        actual should be (expected)
      }
    }

    it("should read in the tiles as squares") {
      val reprojectedRasterSource = rasterSource.withCRS(targetCRS)
      val rdd = RasterSourceRDD(reprojectedRasterSource, layout)

      val values = rdd.values.collect()

      values.map { value => (value.cols, value.rows) should be ((256, 256)) }
    }

    it("should be the same as a tiled HadoopGeoTiffRDD") {
      val floatingLayout = FloatingLayoutScheme(256)

      val geoTiffRDD = HadoopGeoTiffRDD.spatialMultiband(uri)
      val md = geoTiffRDD.collectMetadata[SpatialKey](floatingLayout)._2
      val geoTiffTiledRDD = geoTiffRDD.tileToLayout(md)

      val reprojected: MultibandTileLayerRDD[SpatialKey] =
        geoTiffTiledRDD
          .reproject(
            targetCRS,
            layout,
            Reproject.Options(targetCellSize = Some(layout.cellSize))
          )._2

      val rasterSourceRDD: MultibandTileLayerRDD[SpatialKey] = RasterSourceRDD(rasterSource, md.layout)

      val reprojectedSource =
        rasterSourceRDD
          .reproject(
            targetCRS,
            layout,
            Reproject.Options(targetCellSize = Some(layout.cellSize))
          )._2

      val joinedRDD = reprojected.leftOuterJoin(reprojectedSource)

      joinedRDD.collect().map { case (key, (expected, actualTile)) =>
        actualTile match {
          case Some(actual) => assertEqual(expected, actual)
          case None => throw new Exception(s"Key: key does not exist in the rasterSourceRDD")
        }
      }
    }
  }
}