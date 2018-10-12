package geotrellis.demo.wse

import geotrellis.vector._
import org.geotools.feature.AttributeTypeBuilder
import org.geotools.feature.simple.SimpleFeatureTypeBuilder
import org.opengis.feature.simple.SimpleFeatureType
import spire.math._
import spire.implicits._

object Utils {
  implicit class extentMethods(self: Extent) {
    def splitBySize(xStep: Double, yStep: Double): Iterator[Extent] = {
      for {
        xmin <- Interval(self.xmin, self.xmax).iterator(xStep)
        ymin <- Interval(self.ymin, self.ymax).iterator(yStep)
      } yield Extent(xmin, ymin, math.min(self.xmax, xmin + xStep), math.min(self.ymax, ymin + yStep))
    }

    def splitByCount(xSplits: Int, ySplits: Int): Iterator[Extent] = {
      splitBySize(self.width / xSplits.toDouble, self.height / ySplits.toDouble)
    }
  }

  def renameFID(original: SimpleFeatureType): SimpleFeatureType = {
    val b = new SimpleFeatureTypeBuilder

    b.init(original)
    //add attributes in order
    import scala.collection.JavaConversions._
    for (descriptor <- original.getAttributeDescriptors) {
      val ab = new AttributeTypeBuilder()
      ab.init(descriptor)
      if (descriptor.getLocalName == "FID_1")
        b.add(ab.buildDescriptor("FID"))
      else
        b.add(ab.buildDescriptor(descriptor.getLocalName))
    }

    b.buildFeatureType
  }
}
