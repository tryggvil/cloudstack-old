/* $Id: SourceCode.java,v 1.4 2008/03/30 12:43:17 essmann Exp $
 *
 * Copyright (c) 2002-2008 Bruno Essmann
 * All rights reserved.
 */

package net.sourceforge.taglets.demo.inline;


/**
 * Demonstration of source code inline tags.
 * <p>This demo class contains examples for the various preformatted 
 * inline tags offered by the default configuration of the <em>Taglets 
 * Collection.</em></p>
 * <p>The preformatted inline tags are mainly used to include snippets
 * of source code in your JavaDoc comments and come in two flavours:</p>
 * <dl>
 * <dt><b>Markup style</b></dt>
 * <dd>Preformatted text where HTML markup is left to the user. Also HTML
 * markup has to be escaped by the user, i.e. you have to use <tt>&amp;lt;</tt>
 * in the JavaDoc comment to get a <tt>'&lt;'</tt>.</dd>
 * <dt><b>Prettified style</b></dt>
 * <dd>Preformatted, literal text. HTML markup is escaped automatically and
 * source code is formatted using a handy browser-side prettifier
 * called <a href="http://code.google.com/p/google-code-prettify/">
 * Google Code Prettify</a>.</dd>
 * </dl>
 * {@stickyNote As of JSE 5 the standard JavaDoc doclet also offers two
 * inline tags that are handy for describing source code, 
 * <tt>&#x7b;@code&#x7d;</tt> and <tt>&#x7b;@literal&#x7d;</tt>. The tags
 * offered by the default configuration of the <em>Taglets Collection</em>
 * are rendered in a framed box and are available starting at J2SE 1.4
 * JavaDoc.}
 * @adm $Revision: 1.4 $ $Date: 2008/03/30 12:43:17 $
 * @since 1.9.2
 */
public class SourceCode {

	// ---- Methods

	/**
	 * Demo for the <tt>&#x7b;@example&#x7d;</tt> inline tag.
	 * <p>The &#x7b;@example&#x7d; inline tag denotes literal, preformatted
	 * text. Its contents is rendered using the HTML <tt>&lt;pre&gt;</tt> tag 
	 * and all HTML markup is properly converted. This makes the tag perfectly 
	 * suited for code snippets.</p>
	 * <p>The tag syntax is <tt>&#x7b;@example source code&#x7d;</tt> which will
	 * yield an example tag with the default title &quot;Example:&quot;</p>
	 * {@example Simple @{at example} inline tag.}
	 * <p>Or if you want a custom title use <tt>&#x7b;@example "title" source 
	 * code&#x7d;</tt>:</p>
	 * {@example "Sample Usage"
	 * public void codeSnippet (boolean funny) {
	 *   if (funny) {
	 *     System.out.println("Funny!");
	 *   } else {
	 *     System.out.println("Not funny.");
	 *   }
	 * }}
	 * @since 1.8.2
	 */
	public void example () {
		System.out.println("example");
	}
	
	/**
	 * Demo for the <tt>&#x7b;@markupExample&#x7d;</tt> inline tag.
	 * {@markupExample "Markup Example Tag"
	 * <tt>&#x7b;@markupExample&#x7d;</tt> inline tag.
	 * Similar to the <tt>&#x7b;@example&#x7d;</tt> tag, but is <b>not</b> 
	 * literal or prettified, i.e. you can use HTML tags inside like 
	 * <em>&lt;em/&gt;</em>, <strong>&lt;strong/&gt;</strong>, or colors:
	 * <font color="#800">Dark red text</font>.}
	 * @since 1.9.2
	 */
	public void markupExample () {
		System.out.println("markupExample");
	}

	/**
	 * Demo for the <tt>&#x7b;@source&#x7d;</tt> inline tag.
	 * <p>The &#x7b;@source&#x7d; inline tag denotes literal, preformatted
	 * text. Its contents is rendered using the HTML <tt>&lt;pre&gt;</tt> tag 
	 * and all HTML markup is properly converted. This makes the tag perfectly 
	 * suited for code snippets.</p>
	 * <p>The tag syntax is <tt>&#x7b;@source source code&#x7d;</tt> which will
	 * yield an source tag without title:</p>
	 * {@source Simple @source inline tag.}
	 * <p>Or if you want a title use <tt>&#x7b;@source "title" source 
	 * code&#x7d;</tt>:</p>
	 * {@source "Sample Source"
	 * {@annotation myAnnotation}
	 * public void codeSnippet (boolean funny) {
	 *   if (funny) {
	 *     System.out.println("Funny}!");
	 *   } else {
	 *     System.out.println("Not funny.");
	 *   }
	 * }}
	 * <p>To use the '@' sign in the source code either use the inline tag
	 * <tt>&#x7b;@annotation&#x7d;</tt> for annotations (the example above
	 * uses <tt>&#x7b;@annotation myAnnotation&#x7d;</tt>) or use the 
	 * inline tag <tt>&#x7b;@at&#x7d;</tt>, they are equivalent (e.g.
	 * using <tt>&#x7b;@at&#x7d;</tt> without text will simply produce
	 * the '@' sign in the javadoc).</p>
	 * @since 1.8.2
	 */
	public void source () {
		System.out.println("source");
	}
	
	/**
	 * Demo for the <tt>&#x7b;@markupSource&#x7d;</tt> message tag.
	 * {@markupSource "Markup Source Tag"
	 * <tt>&#x7b;@markupSource&#x7d;</tt> inline tag.
	 * Similar to the <tt>&#x7b;@source&#x7d;</tt> tag, but <b>not</b> 
	 * literal or prettified, i.e. you can use HTML tags inside like 
	 * <em>&lt;em/&gt;</em>, <strong>&lt;strong/&gt;</strong>, or colors:
	 * <font color="#800">Dark red text</font>.}
	 * @since 1.9.2
	 */
	public void markupSource () {
		System.out.println("markupSource");
	}
	
	/**
	 * Demo for the <tt>&#x7b;@at&#x7d;</tt> inline tag.
	 * <p>Sometimes you might want to include an at sign '@' in an inline
	 * tag like <tt>&#x7b;@example&#x7d;</tt>. However you cannot simply
	 * write <tt>&#x7b;@example foo &#x40;bar quux&#x7d;</tt> since JavaDoc
	 * will try to evaluate &#x40;bar as tag. Instead write <tt>&#x7b;@example 
	 * foo &#x7b;@bar&#x7d; quux&#x7d;</tt>.</p>
	 * {@example foo @{at bar} quux}
	 * @since 2.0.3
	 */
	public void at () {
		System.out.println("at");
	}

	/**
	 * Demo for the <tt>&#x7b;@annotation&#x7d;</tt> inline tag.
	 * <p>To include annotations in your source inline tags convert them
	 * using the <tt>&#x7b;@annotation&#x7d;</tt> inline tag. This tag
	 * works just like the {@link #at()} inline tag, except that it has
	 * a different name which implies that it's used to mark an annotation.</p>
	 * {@source "Sample Source"
	 * {@annotation myAnnotation}
	 * public void codeSnippet (boolean funny) {
	 *   if (funny) {
	 *     System.out.println("Funny}!");
	 *   } else {
	 *     System.out.println("Not funny.");
	 *   }
	 * }}
	 * @since 2.0.3
	 */
	public void annotation () {
		System.out.println("annotation");
	}

}
