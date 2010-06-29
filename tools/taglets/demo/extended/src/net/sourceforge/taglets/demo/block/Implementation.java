/* $Id: Implementation.java,v 1.4 2008/03/30 12:43:23 essmann Exp $
 *
 * Copyright (c) 2002-2008 Bruno Essmann
 * All rights reserved.
 */

package net.sourceforge.taglets.demo.block;

import net.sourceforge.taglets.demo.Demo;


/**
 * Demonstration of block tags that show implementation details.
 * @adm $Revision: 1.4 $ $Date: 2008/03/30 12:43:23 $
 * @since 1.9.2
 */
public class Implementation
	extends Demo
{
	
	// ---- Static
	
	/**
	 * Constant demo.
	 * <p>By default the taglest collection adds an invisible block tag called
	 * <tt>&#x40;constant</tt> that contains the value of the constant.</p>
	 * <p>The value is created as a link into the constant values list generated
	 * by JavaDoc.</p>
	 * @since 1.9.2
	 */
	public static final String CONSTANT= "ConstantValue";
	
	/**
	 * Constant override demo.
	 * <p>You can override the constant value automatically added by explicitly
	 * declaring a <tt>&#x40;constant</tt> block with any text you would like
	 * to see listed.</p>
	 * <p>When overriding the constant tag no link is generated!</p>
	 * {@markupExample "Example:"
	 * &#x2F;&#x2A;&#x2A;
	 *  &#x2A; &#x40;constant Used internally, do not use.
	 *  &#x2A;&#x2F;}
	 * @constant Used internally, do not use.
	 * @since 1.9.2
	 */
	public static final String CONSTANT_OVERRIDE= "ConstantOverrideValue";

	// ---- Methods

	/**
	 * Demo for the <tt>@equivalence</tt> block tag.
	 * <p>The <tt>@equivalence</tt> block tag can be used to describe convenience
	 * methods. Usually they are used to simplify usage of a class by providing
	 * a simple shortcut for often used complex method calls.</p>
	 * <p>The equivalence tag usually describes a code sequence that, except for
	 * performance is equivalent to the code shown.</p>
	 * <p>Check out {@link #toString()} for an example usage.</p>
	 * {@markupExample "Usage of the @equivalence tag:"
	 * &#x2F;&#x2A;&#x2A;
	 *  &#x2A; &#x40;equivalence anImplementation.impl();
	 *  &#x2A;&#x2F;}
	 * @equivalence impl();
	 * @see #toString()
	 * @since 1.9.2
	 */
	public void equivalence () {
		System.out.println("equivalence");
	}
	
	/**
	 * Demo for the <tt>@impl</tt> block tag.
	 * <p>The <tt>@impl</tt> block tag is used to describe an implementation
	 * detail. It is especially handy when overriding a method (or implementing 
	 * an interface method) and you want to keep the original method comment
	 * but want to add an additional implementation note.</p>
	 * <p>To &quot;inherit&quot; the superclass documentation and simply add 
	 * an implementation note use a JavaDoc comment in the following form:</p>
	 * {@markupExample "Usage of the @impl tag:"
	 * &#x2F;&#x2A;&#x2A;
	 *  &#x2A; &#x40;impl Description of the implementation detail.
	 *  &#x2A;&#x2F;}
	 * {@stickyNote <p>Make sure that you only use block tags if you want to 
	 * automatically &quot;inherit&quot; the superclass documentation.</p>
	 * <p>Check out {@link #toString()} for an example usage.</p>}
	 * @impl Description of the implementation detail.
	 * @see #toString()
	 * @since 1.9.2
	 */
	public void impl () {
		System.out.println("impl");
	}
	
	/**
	 * Demo for the <tt>@testcase</tt> block tag.
	 * <p>The <tt>@testcase</tt> may be used to list all unit test cases relevant
	 * for the given method and/or class.</p>
	 * @testcase net.sourceforge.taglets.demo.block.ImplementationTest
	 * @testcase net.sourceforge.taglets.demo.block.AnotherImplementationTest
	 * @since 1.9.2
	 */
	public void testcase () {
	}
	
	/**
	 * @equivalence 
	 * getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
	 * @impl Returns the string representation of this object using the
	 * simple class name instead of the fully qualified class name.
	 * @since 1.9.2
	 */
	public String toString () {
		return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
	}
	
}
