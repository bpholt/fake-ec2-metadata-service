package dev.holt.imds

import cats.*
import cats.kernel.laws.discipline.EqTests
import cats.laws.discipline.MonadTests
import dev.holt.imds.JavaOptionalInstances.*
import munit.DisciplineSuite
import org.scalacheck.{Arbitrary, Gen}

import java.util.Optional
import scala.jdk.OptionConverters.RichOption

object JavaOptionalInstances {
  implicit def optionalMonad: Monad[Optional] = new Monad[Optional] {
    override def pure[A](x: A): Optional[A] = Optional.of(x)

    override def flatMap[A, B](fa: Optional[A])(f: A => Optional[B]): Optional[B] =
      fa.flatMap(a => f(a))

    override def tailRecM[A, B](a: A)(f: A => Optional[Either[A, B]]): Optional[B] =
      f(a).flatMap {
        case Left(a1) => tailRecM(a1)(f)
        case Right(b) => Optional.of(b)
      }
  }

  implicit def eqOptional[A : Eq]: Eq[Optional[A]] = new Eq[Optional[A]] {
    override def eqv(x: Optional[A], y: Optional[A]): Boolean = {
      if (!x.isPresent && !y.isPresent) true
      else
        (for {
          xx <- x
          yy <- y
        } yield Eq[A].eqv(xx, yy)).orElseGet(() => false)
    }
  }
}

class JavaOptionalSpec extends DisciplineSuite {
  implicit def arbOptional[A : Arbitrary]: Arbitrary[Optional[A]] = Arbitrary(Gen.option(Arbitrary.arbitrary[A]).map(_.toJava))
  implicit def arbOptionalFunction[A]: Arbitrary[Optional[A] => Optional[A]] = Arbitrary {
    Gen.option(Gen.const(()))
      .flatMap { (y: Option[Unit]) =>
        Gen.const {
          (x: Optional[A]) => x.filter(_ => y.isDefined)
        }
      }
  }

  checkAll("java.util.Optional.MonadLaws", MonadTests[Optional].stackUnsafeMonad[String, Int, String])
  checkAll("java.util.Optional.EqLaws", EqTests[Optional[Int]].eqv)
}
