/* $Id: Demo.java,v 1.7 2008/03/30 12:43:23 essmann Exp $
 *
 * Copyright (c) 2002-2008 Bruno Essmann
 * All rights reserved.
 */

package net.sourceforge.taglets.demo;


/**
 * Demo class.
 * @adm $Revision: 1.7 $ $Date: 2008/03/30 12:43:23 $
 * (no since - causes warning)
 */
public abstract class Demo {
	
	// ---- Constructors

	/**
	 * Creates a new <code>Demo</code> object.
	 * @since 1.9.2
	 */
	protected Demo () {
		// Nop.
	}
	
	// ---- Methods
	
	/**
	 * Shows off the usage of some stickies included.
	 * {@stickyNote Stickies are perfect to gain attention!}
	 * {@stickyInfo Stickies come in various flavours.}
	 * {@stickyDone "Custom Titles:" Of course all stickies
	 * can have custom titles.}
	 * {@stickyWarning <p>Stickies can have multiple paragraphs and of course
	 * contain other inline tags:</p>
	 * {@source Demo.showPopup("Stickies are fun!");}}
	 * {@stickyError Sticky with an <tt>@stickyError</tt> message.}
	 * @since 1.9.2
	 */
	public void stickies () {
		System.out.println("stickies");
	}
	
	/**
	 * Block tags as eye catchers.
	 * @todo I'm sure there are always things left to do for you...
	 * @info What follows are a couple of random quotations to show off the 
	 * looks of the iconized block tags.
	 * @note It is the mark of an educated mind to be able to entertain a 
	 * thought without accepting it.<br/>
	 * <em style="font-size: 80%; font-weight: bold;">
	 * Aristotle (384 BC - 322 BC)</em>
	 * @done You cannot acquire experience by making experiments. 
	 * You cannot create experience. You must undergo it.<br/>
	 * <em style="font-size: 80%; font-weight: bold;">
	 * Albert Camus (1913 - 1960)</em>
	 * @warning Doubt is not a pleasant condition, but certainty is absurd.<br/>
	 * <em style="font-size: 80%; font-weight: bold;">
	 * Voltaire (1694 - 1778)</em>
	 * @error I am not bound to please thee with my answers.<br/>
	 * <em style="font-size: 80%; font-weight: bold;">
	 * William Shakespeare (1564 - 1616)</em>
	 * @since 1.9.2
	 */
	public void blocks () {
		System.out.println("blocks");
	}
	
	/**
	 * Returns a string representation of this object. 
	 * <p>In general, the <code>toString</code> method returns a string that
	 * &quot;textually represents&quot; this object. The result should be a 
	 * concise but informative representation that is easy for a person to read.
	 * It is recommended that all subclasses override this method.</p>
	 * {@stickyInfo "Default Implementation:"
	 * The default implementation of <code>toString</code> returns a string 
	 * consisting of the fully qualified name of the class of which the object 
	 * is an instance, the at-sign character `<code>@</code>', and the unsigned 
	 * hexadecimal representation of the hash code of the object. In other words, 
	 * this method returns a string equal to the value of:
	 * {@source getClass().getName() + '@' + Integer.toHexString(hashCode())}
	 * }
	 * @since 1.9.2
	 */
	public String toString () {
		return super.toString();
	}
	
}
