package spinal.lib.misc.plugin

import spinal.core._
import spinal.core.fiber._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

class FiberPlugin extends Area with Hostable {
  this.setName(ClassName(this))

  def withPrefix(prefix: String) = setName(prefix + "_" + getName())

  def retains(that: Seq[Nameable]) = RetainerGroup(that)
  def retains(head: Nameable, tail: Nameable*) = RetainerGroup(head +: tail)


  var pluginEnabled = true
  var host : PluginHost = null

  val subservices = ArrayBuffer[Any]()
  def addService[T](that : T) : T = {
    subservices += that
    that
  }

  def awaitBuild() = Fiber.awaitBuild()

  val lockables = mutable.LinkedHashSet[() => Lock]()
//  val retainers = mutable.LinkedHashSet[() => Retainer]()
//  val retainersHold = mutable.LinkedHashSet[() => RetainerHold]()
  def buildBefore(l : => Lock): Unit = {
    if (lockables.isEmpty) {
      spinal.core.fiber.Fiber.setupCallback {
        val things = lockables.map(_())
        things.foreach(_.retain())
        if (buildCount == 0) {
          during build{}
        }
      }
    }
    lockables += (() => l)
  }

  def setupRetain(l: => Lock): Unit = {
    spinal.core.fiber.Fiber.setupCallback {
      l.retain()
    }
  }

//  def buildBefore(l: => Retainer): Unit = {
//    if (lockables.isEmpty) {
//      spinal.core.fiber.Fiber.setupCallback {
//        val things = lockables.map(_())
//        things.foreach(retainersHold += _)
//        if (buildCount == 0) {
//          during build {}
//        }
//      }
//    }
//  }
//
//  def setupRetain(l: => Retainer): Unit = {
//    spinal.core.fiber.Fiber.setupCallback {
//      l.retain()
//    }
//  }

  var buildCount = 0


  override def setHost(h: PluginHost): Unit = {
    h.addService(this)
    subservices.foreach(h.addService)
    host = h
  }

  def during = new {
    def setup[T: ClassTag](body: => T): Handle[T] = spinal.core.fiber.Fiber setup {
      pluginEnabled generate {
        host.rework(body)
      }
    }

    def build[T: ClassTag](body: => T): Handle[T] = {
      buildCount += 1
      spinal.core.fiber.Fiber build {
        pluginEnabled generate {
          val ret = host.rework(body)
          buildCount -= 1
          if (buildCount == 0) {
            lockables.foreach(_().release())
          }
          ret
        }
      }
    }
  }

  override def valCallbackRec(obj: Any, name: String) = {
    obj match {
//      case obj : NamedType[_] => obj.setName(name)
      case _ => super.valCallbackRec(obj, name)
    }
  }
}