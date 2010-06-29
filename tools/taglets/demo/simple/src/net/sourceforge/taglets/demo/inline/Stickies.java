/* $Id: Stickies.java,v 1.3 2008/03/30 12:43:17 essmann Exp $
 *
 * Copyright (c) 2002-2008 Bruno Essmann
 * All rights reserved.
 */

package net.sourceforge.taglets.demo.inline;


/**
 * Demonstration of &quot;Sticky&quot; inline tags.
 * {@stickyNote "Stickies" 
 * <p>The <em>Taglets Collection</em> provides several predefined inline
 * tags that can be used to create &quot;Sticky Notes&quot; like this one.</p>
 * <p>Check out the method descriptions to see the various sticky types.</p>}
 * @adm $Revision: 1.3 $ $Date: 2008/03/30 12:43:17 $
 * @since 1.8.2
 */
public class Stickies {
	
	// ---- Methods

	/**
	 * Demo for the <tt>&#x7b;@stickyInfo&#x7d;</tt> message tag.
	 * {@stickyInfo Simple <tt>&#x7b;@stickyInfo&#x7d;</tt> message.}
	 * {@stickyInfo "Custom Title" Sticky info with custom title.}
	 * {@stickyInfo "Multi Paragraph" 
	 * <p>Sticky info with custom title.</p>
	 * <p>This is the second paragraph.</p>}
	 * @since 1.8.2
	 */
	public void stickyInfo () {
		System.out.println("stickyInfo");
	}

	/**
	 * Demo for the <tt>&#x7b;@stickyNote&#x7d;</tt> message tag.
	 * {@stickyNote Simple <tt>&#x7b;@stickyNote&#x7d;</tt> message.}
	 * {@stickyNote "Custom Title" Sticky note with custom title.}
	 * {@stickyNote "Multi Paragraph" 
	 * <p>Sticky note with custom title.</p>
	 * <p>This is the second paragraph.</p>}
	 * @since 1.8.2
	 */
	public void stickyNote () {
		System.out.println("stickyNote");
	}

	/**
	 * Demo for the <tt>&#x7b;@stickyDone&#x7d;</tt> message tag.
	 * {@stickyDone Simple <tt>&#x7b;@stickyDone&#x7d;</tt> message.}
	 * {@stickyDone "Custom Title" Sticky done message with custom title.}
	 * {@stickyDone "Multi Paragraph" 
	 * <p>Sticky done message with custom title.</p>
	 * <p>Containing several paragraphs.</p>}
	 * @since 1.8.2
	 */
	public void stickyDone () {
		System.out.println("stickyDone");
	}
	
	/**
	 * Demo for the <tt>&#x7b;@stickyWarning&#x7d;</tt> message tag.
	 * {@stickyWarning Simple <tt>&#x7b;@stickyWarning&#x7d;</tt> message.}
	 * {@stickyWarning "Custom Title" Sticky warning with custom title.}
	 * {@stickyWarning "Multi Paragraph" 
	 * <p>Sticky warning with custom title.</p>
	 * <p>This is the second paragraph.</p>}
	 * @since 1.8.2
	 */
	public void stickyWarning () {
		System.out.println("stickyWarning");
	}

	/**
	 * Demo for the <tt>&#x7b;@stickyError&#x7d;</tt> message tag.
	 * {@stickyError Simple <tt>&#x7b;@stickyError&#x7d;</tt> message.}
	 * {@stickyError "Custom Title" Sticky error message with custom title.}
	 * {@stickyError "Multi Paragraph" 
	 * <p>Sticky error message with custom title.</p>
	 * <p>Containing several paragraphs.</p>}
	 * @since 1.8.2
	 */
	public void stickyError () {
		System.out.println("stickyError");
	}

}
