package com.thoughtworks.deepLearning.array2D.ast

import cats._
import cats.implicits._
import org.nd4s.Implicits._
import com.thoughtworks.deepLearning.Batch._
import com.thoughtworks.deepLearning.Ast._
import com.thoughtworks.deepLearning.array2D.utilities._
import com.thoughtworks.deepLearning.{Ast, Batch}
import com.thoughtworks.deepLearning.seq2D.utilities.{Seq2D, Seq2DBatch}
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.ops.transforms.Transforms

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final case class ToSeq[Input0 <: Batch](operand: WidenAst[Input0, Array2D#Widen]) extends Cached {
  override type Input = Input0

  final class SharedBatch private[ToSeq] (override val input: Input, upstream: Array2D#Widen)
      extends ReferenceCount
      with Seq2DBatch {

    def zeroDelta =
      upstream.value.map { upstreamData =>
        Nd4j.zeros(upstreamData.shape: _*)
      }.memoize

    @volatile
    var upstreamDelta = zeroDelta

    override protected def flush(): Unit = {
      upstream.backward(synchronized {
        val oldDelta = upstreamDelta
        upstreamDelta = zeroDelta
        oldDelta
      })
    }

    override protected def closeUpstreams(): Unit = {
      upstream.close()
    }

    override def backward(delta: Delta): Unit = {
      synchronized {
        val (i, j, value) = delta.value
        upstreamDelta.value(i, j) = upstreamDelta
            .value(i, j) + value // Cannot use += because of https://issues.scala-lang.org/browse/SI-10021
      }
    }

    override val value: Data = {
      upstream.value.map { ndarray: INDArray =>
        val doubleArray = ndarray.data.asDouble()
        for (i <- (0 until ndarray.rows).view) yield {
          doubleArray.view(i * ndarray.columns, (i + 1) * ndarray.columns)
        }
      }.memoize
    }
  }

  /**
    * Performs the underlying forward pass.
    *
    * @return a [[Batch]] that will be cached for subsequent [[#forward]]
    */
  override protected def rawForward(input: Input): SharedBatch = {
    new SharedBatch(input, operand.forward(input))
  }
}
