<%!
TYPE_VARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
MAX_ARITY = 22

def nested_tuples(n):
    """
    >>> nested_tuples(2)
    (a, b)
    >>> nested_tuples(3)
    ((a, b), c)
    >>> nested_tuples(5)
    '((((a, b), c), d), e)'
    """
    def pairwise(elems):
        if not elems: return ''
        elif len(elems) == 1: return elems[0]
        else: return '({}, {})'.format(pairwise(elems[:-1]), elems[-1])
    return pairwise(TYPE_VARS.lower()[:n])

def flat_tuple(n):
    return '({})'.format(', '.join(TYPE_VARS.lower()[:n]))
%>\
package com.twitter.util.testing

import java.util.{List => JList}
import org.mockito.ArgumentCaptor
import org.mockito.exceptions.Reporter
import scala.collection.JavaConverters._
import scala.reflect._

// This file was generated from codegen/util-test/ArgumentCapture.scala.mako

trait ArgumentCapture {
  /**
   * Enables capturingOne to be implemented over capturingAll with the same behavior as ArgumentCaptor.getValue
   */
  private[this] def noArgWasCaptured(): Nothing = {
    new Reporter().noArgumentValueWasCaptured() // this always throws an exception
    throw new RuntimeException("this should be unreachable, but allows the method to be of type Nothing")
  }

  /**
   * Capture all the invocations from a verify(mock).method(arg) call.
   *
   * Example:
   *   val requests = capturingAll(verify(myAPIEndpoint, times(4)).authenticate _)
   *   requests.length must equal (4)
   */
  def capturingAll[T: ClassTag](f: T => _): Seq[T] = {
    val argCaptor = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    f(argCaptor.capture())
    argCaptor.getAllValues.asScala.toSeq
  }

  /**
   * Capture an argument from a verify(mock).method(arg) call.
   *
   * Example:
   *   val request = capturingOne(verify(myAPIEndpoint).authenticate _)
   *   request.userId must equal (123L)
   *   request.password must equal ("reallySecurePassword")
   */
  def capturingOne[T: ClassTag](f: T => _): T =
    capturingAll[T](f).lastOption.getOrElse(noArgWasCaptured())\

  % for i in xrange(2, MAX_ARITY + 1):
<%
    types = ', '.join(TYPE_VARS[:i])
    types_with_class_tags = ', '.join('{}: ClassTag'.format(t) for t in TYPE_VARS[:i])
    iterable_args = ', '.join('arg{}: Iterable[{}]'.format(j, type) for j, type in enumerate(TYPE_VARS[:i]))
%>
  /** Zip ${i} iterables together into a Seq of ${i}-tuples. */
  private[this] def zipN[${types}](${iterable_args}): Seq[(${types})] = {
  % if i == 2:
    arg0.zip(arg1).toSeq
  % else:
    arg0
    % for j in xrange(1, i):
      .zip(arg${j})
    % endfor
      .map({ case ${nested_tuples(i)} => ${flat_tuple(i)} })
      .toSeq
  % endif
  }

  /** Capture all invocations of a mocked ${i}-ary method */
  def capturingAll[${types_with_class_tags}](func: (${types}) => _): Seq[(${types})] = {
    % for type in TYPE_VARS[:i]:
    val argCaptor${type} = ArgumentCaptor.forClass(classTag[${type}].runtimeClass.asInstanceOf[Class[${type}]])
    % endfor
    func(${', '.join("argCaptor{}.capture()".format(type) for type in TYPE_VARS[:i])})
    % for type in TYPE_VARS[:i]:
    val args${type} = argCaptor${type}.getAllValues.asScala
    % endfor
    zipN(${', '.join("args{}".format(type) for type in TYPE_VARS[:i])})
  }

  /** Capture one invocation of a mocked ${i}-ary method */
  def capturingOne[${types_with_class_tags}](func: (${types}) => _): (${types}) =
    capturingAll[${types}](func).lastOption.getOrElse(noArgWasCaptured())
  % endfor

}
