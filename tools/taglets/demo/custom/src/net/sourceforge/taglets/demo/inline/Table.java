/* $Id: Table.java,v 1.3 2008/03/30 12:43:17 essmann Exp $
 *
 * Copyright (c) 2002-2008 Bruno Essmann
 * All rights reserved.
 */

package net.sourceforge.taglets.demo.inline;


/**
 * Demonstration of the table inline tag.
 * <p>This demo class shows how to use the table inline tag.</p>
 * <p>Instead of fiddling with HTML tables in JavaDoc source code the
 * table inline tags allows for nicely readable tables, esp. when using
 * a monospaced font to edit the source code.</p>
 * <p>The &#x40;table tag is handy to create <tt>M*N</tt> tables and is
 * used as follows:</p>
 * {@markupSource "Table Tag Syntax"
 * &#x7b;&#x40;table [noheader] [nofill]
 *   || header 1 | header 2 | ... | header N ||
 *   || cell 1/1 | cell 1/2 | ... | cell 1/N ||
 *   || cell 2/1 | cell 2/2 | ... | cell 2/N ||
 *   ...
 *   || cell M/1 | cell M/2 | ... | cell M/N ||
 * &#x7d;}
 * {@stickyNote "Customization"
 * In the default configuration the &#x40;table tag uses bold header text
 * with a dark background. Table rows use an alternating background color.
 * You can easily change the default style or introduce new table tags with
 * different CSS styles via the taglets configuration.}
 * @adm $Revision: 1.3 $ $Date: 2008/03/30 12:43:17 $
 * @since 1.9.2
 */
public class Table {

	// ---- Methods

	/**
	 * Table with header that uses up the available browser width.
	 * <p>The following inline tag produces the table shown below:</p>
	 * {@markupSource
	 * &#x7b;&#x40;table
	 *   || header 1 | header 2 | header 3 | header 4 | header 5 ||
	 *   || cell 1/1 | cell 1/2 | cell 1/3 | cell 1/4 | cell 1/5 ||
	 *   || cell 2/1 | cell 2/2 | cell 2/3 | cell 2/4 | cell 2/5 ||
	 *   || cell 3/1 | cell 3/2 | cell 3/3 | cell 3/4 | cell 3/5 ||
	 *   || cell 4/1 | cell 4/2 | cell 4/3 | cell 4/4 | cell 4/5 ||
	 * &#x7d;}
	 * {@table 
	 *   || header 1 | header 2 | header 3 | header 4 | header 5 ||
	 *   || cell 1/1 | cell 1/2 | cell 1/3 | cell 1/4 | cell 1/5 ||
	 *   || cell 2/1 | cell 2/2 | cell 2/3 | cell 2/4 | cell 2/5 ||
	 *   || cell 3/1 | cell 3/2 | cell 3/3 | cell 3/4 | cell 3/5 ||
	 *   || cell 4/1 | cell 4/2 | cell 4/3 | cell 4/4 | cell 4/5 ||
	 * }
	 * @since 1.9.2
	 */
	public void defaultTable () {
		System.out.println("defaultTable");
	}
	
	/**
	 * Table without header that uses up the available browser width.
	 * <p>The following inline tag produces the table shown below:</p>
	 * {@markupSource
	 * &#x7b;&#x40;table noheader
	 *   || cell 1/1 | cell 1/2 | cell 1/3 | cell 1/4 | cell 1/5 ||
	 *   || cell 2/1 | cell 2/2 | cell 2/3 | cell 2/4 | cell 2/5 ||
	 *   || cell 3/1 | cell 3/2 | cell 3/3 | cell 3/4 | cell 3/5 ||
	 *   || cell 4/1 | cell 4/2 | cell 4/3 | cell 4/4 | cell 4/5 ||
	 * &#x7d;}
	 * {@table noheader
	 *   || cell 1/1 | cell 1/2 | cell 1/3 | cell 1/4 | cell 1/5 ||
	 *   || cell 2/1 | cell 2/2 | cell 2/3 | cell 2/4 | cell 2/5 ||
	 *   || cell 3/1 | cell 3/2 | cell 3/3 | cell 3/4 | cell 3/5 ||
	 *   || cell 4/1 | cell 4/2 | cell 4/3 | cell 4/4 | cell 4/5 ||
	 * }
	 * @since 1.9.2
	 */
	public void noHeader () {
		System.out.println("noHeader");
	}

	/**
	 * Table with header that only uses the required width.
	 * <p>The following inline tag produces the table shown below:</p>
	 * {@markupSource
	 * &#x7b;&#x40;table nofill
	 *   || header 1 | header 2 | header 3 | header 4 | header 5 ||
	 *   || cell 1/1 | cell 1/2 | cell 1/3 | cell 1/4 | cell 1/5 ||
	 *   || cell 2/1 | cell 2/2 | cell 2/3 | cell 2/4 | cell 2/5 ||
	 *   || cell 3/1 | cell 3/2 | cell 3/3 | cell 3/4 | cell 3/5 ||
	 *   || cell 4/1 | cell 4/2 | cell 4/3 | cell 4/4 | cell 4/5 ||
	 * &#x7d;}
	 * {@table nofill
	 *   || header 1 | header 2 | header 3 | header 4 | header 5 ||
	 *   || cell 1/1 | cell 1/2 | cell 1/3 | cell 1/4 | cell 1/5 ||
	 *   || cell 2/1 | cell 2/2 | cell 2/3 | cell 2/4 | cell 2/5 ||
	 *   || cell 3/1 | cell 3/2 | cell 3/3 | cell 3/4 | cell 3/5 ||
	 *   || cell 4/1 | cell 4/2 | cell 4/3 | cell 4/4 | cell 4/5 ||
	 * }
	 * @since 1.9.2
	 */
	public void noFill () {
		System.out.println("noFill");
	}
	
	/**
	 * Table without header that only uses the required width.
	 * <p>The following inline tag produces the table shown below:</p>
	 * {@markupSource
	 * &#x7b;&#x40;table noheader nofill
	 *   || cell 1/1 | cell 1/2 | cell 1/3 | cell 1/4 | cell 1/5 ||
	 *   || cell 2/1 | cell 2/2 | cell 2/3 | cell 2/4 | cell 2/5 ||
	 *   || cell 3/1 | cell 3/2 | cell 3/3 | cell 3/4 | cell 3/5 ||
	 *   || cell 4/1 | cell 4/2 | cell 4/3 | cell 4/4 | cell 4/5 ||
	 * &#x7d;}
	 * {@table noheader nofill
	 *   || cell 1/1 | cell 1/2 | cell 1/3 | cell 1/4 | cell 1/5 ||
	 *   || cell 2/1 | cell 2/2 | cell 2/3 | cell 2/4 | cell 2/5 ||
	 *   || cell 3/1 | cell 3/2 | cell 3/3 | cell 3/4 | cell 3/5 ||
	 *   || cell 4/1 | cell 4/2 | cell 4/3 | cell 4/4 | cell 4/5 ||
	 * }
	 * @since 1.9.2
	 */
	public void noHeaderNoFill () {
		System.out.println("noHeaderNoFill");
	}

}
