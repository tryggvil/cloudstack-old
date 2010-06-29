/* $Id: DateTimeInlineTaglet.java,v 1.2 2008/03/30 12:43:23 essmann Exp $
 *
 * Copyright (c) 2002-2008 Bruno Essmann
 * All rights reserved.
 */

package net.sourceforge.taglets.simple.inline;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import net.sourceforge.taglets.simple.Configuration;
import net.sourceforge.taglets.simple.InlineTaglet;
import net.sourceforge.taglets.simple.Logger;
import net.sourceforge.taglets.simple.Parameter;
import net.sourceforge.taglets.simple.filter.LiteralTextFilter;
import net.sourceforge.taglets.simple.filter.TextFilter;
import net.sourceforge.taglets.simple.impl.AbstractSimpleTaglet;


/**
 * Custom inline taglet that displays the JavaDoc build date and/or time.
 * <p>The {@className} generates the date/time string as its output
 * when used in a JavaDoc comment using <tt>&#x7b;&#x40;dateTime&#x7d;</tt>.</p>
 * <p>The output format can be configured using the following configuration
 * options:</p>
 * {@table 
 * || Option | Value | Default ||
 * || format | The format to use, see {@link SimpleDateFormat}. | 
 *    <tt>yyyy/MM/dd HH:mm:ss.</tt> ||
 * || language | The language to use while formatting (lowercase ISO 639 code),
 *    see {@link Locale#getLanguage()}. | 
 *    {@link Locale#getDefault() Language of the default locale} ||
 * }
 * <p>When used without any options, i.e. <tt>&#x7b;&#x40;dateTime&#x7d;</tt>
 * the date/time is generated as preconfigured. In addition if a special format
 * is required it can be passed as a option in {@link SimpleDateFormat} style:
 * <tt>&#x7b;&#x40;dateTime EE yyyy MMMM dd&#x7d;</tt>
 * {@stickyInfo "Examples:"
 * <tt>&#x7b;&#x40;dateTime&#x7d;</tt> -&gt; {@dateTime}<br/>
 * <tt>&#x7b;&#x40;dateTime EE yyyy MMMM dd&#x7d;</tt> -&gt; 
 * {@dateTime EE yyyy MMMM dd}}
 * @adm $Revision: 1.2 $ $Date: 2008/03/30 12:43:23 $
 * @since 2.0.0
 */
public class DateTimeInlineTaglet
	extends AbstractSimpleTaglet
	implements InlineTaglet
{

	// ---- Static

	/**
	 * Parameter key for the output {@link SimpleDateFormat format}.
	 * @see #DEFAULT_FORMAT
	 * @since 2.0.0
	 */
	protected static final String PARAM_FORMAT= "format";

	/**
	 * The default output {@link SimpleDateFormat format}.
	 * @see #PARAM_FORMAT
	 * @since 2.0.0
	 */
	protected static final String DEFAULT_FORMAT= "yyyy/MM/dd HH:mm:ss";

	/**
	 * Parameter key for the output language (lowercase ISO 639 code).
	 * @see Locale#getDefault()
	 * @since 2.0.0
	 */
	protected static final String PARAM_LANGUAGE= "language";
	
	// ---- State

	/**
	 * The default output date format.
	 * @see #configure(Logger, Configuration)
	 * @see #getDefaultDateFormat()
	 * @see #setDefaultDateFormat(DateFormat)
	 * @since 2.0.0
	 */
	private DateFormat defaultDateFormat;

	/**
	 * The default output locale.
	 * @see #configure(Logger, Configuration)
	 * @see #getDefaultLocale()
	 * @see #setDefaultLocale(Locale)
	 * @since 2.0.0
	 */
	private Locale defaultLocale;
	
	/**
	 * Literal text filter used to escape HTML entities.
	 * @since 2.0.0
	 */
	private final LiteralTextFilter literalFilter;
	
	// ---- Constructors
	
	/**
	 * Creates a new {@link InlineTaglet} creates a configurable date/time string.
	 * @param name the name of the taglet.
	 * @since 2.0.0
	 */
	public DateTimeInlineTaglet (String name) {
		super(name);
		this.literalFilter= new LiteralTextFilter(true);
	}
	
	// ---- Methods

	/**
	 * Returns the default date format to use if no option is specified.
	 * @return the default date format, <code>null</code> if the taglet is
	 * not configured or there was a configuration error.
	 * @see #configure(Logger, Configuration)
	 * @see #setDefaultDateFormat(DateFormat)
	 * @since 2.0.0
	 */
	protected DateFormat getDefaultDateFormat () {
		return this.defaultDateFormat;
	}
	
	/**
	 * Sets the default output date format.
	 * @param dateFormat the output date format.
	 * @see #configure(Logger, Configuration)
	 * @see #getDefaultDateFormat()
	 * @since 2.0.0
	 */
	protected void setDefaultDateFormat (DateFormat dateFormat) {
		this.defaultDateFormat= dateFormat;
	}

	/**
	 * Returns the default locale output is done for.
	 * @return the default locale, <code>null</code> if the taglet is
	 * not configured or there was a configuration error.
	 * @see #configure(Logger, Configuration)
	 * @see #setDefaultLocale(Locale)
	 * @since 2.0.0
	 */
	protected Locale getDefaultLocale () {
		return this.defaultLocale;
	}
	
	/**
	 * Sets the default locale output is done for.
	 * @param locale the default locale to set.
	 * @see #configure(Logger, Configuration)
	 * @see #getDefaultLocale()
	 * @since 2.0.0
	 */
	protected void setDefaultLocale (Locale locale) {
		this.defaultLocale= locale;
	}
	
	/**
	 * Returns a text filter that escapes HTML entities.
	 * @return the literal text filter requested, never <code>null</code>.
	 * @since 2.0.0
	 */
	protected final TextFilter getLiteralTextFilter () {
		return this.literalFilter;
	}
	
	// ---- SimpleTaglet

	/**
	 * @impl Configures the {@link #getDefaultDateFormat() default date format}.
	 * @see #setDefaultDateFormat(DateFormat)
	 * @see #getDefaultDateFormat()
	 * @since 2.0.0
	 */
	public void configure (Logger logger, Configuration configuration) {
		super.configure(logger, configuration);
		try {
			final String language= configuration.getString(PARAM_LANGUAGE, null);
			setDefaultLocale(
				language == null ? Locale.getDefault() : new Locale(language)
			);
			setDefaultDateFormat(
				new SimpleDateFormat(
					configuration.getString(PARAM_FORMAT, DEFAULT_FORMAT), 
					getDefaultLocale()
				)
			);
		} catch (Exception configurationException) {
			logger.warning("Error configuring '" + getName() + "'.");
			logger.warning(configurationException.getMessage());
		}
	}
	
	// ---- SimpleInlineTaglet
	
	/**
	 * @impl Generates a formatted output of the current date and time. See class
	 * documentation for configuration and <code>text</code> options supported.
	 * @since 2.0.0
	 */
	public String getOutput (Logger logger, Parameter parameter, String text) {
		final String customFormat= (text == null) ? "" : text.trim();
		final DateFormat dateFormat;
		if (customFormat.length() > 0) {
			if (getDefaultLocale() == null) {
				logger.warning("Taglet not configured.");
				return "";
			}
			try {
				dateFormat= new SimpleDateFormat(customFormat, getDefaultLocale());
			} catch (IllegalArgumentException patternException) {
				logger.warning("Illegal date pattern '" + customFormat + "'.");
				logger.warning(patternException.getMessage());
				return "";
			}
		} else {
			dateFormat= getDefaultDateFormat();
			if (dateFormat == null) {
				logger.warning("Taglet not configured.");
				return "";
			}
		}
		// Return the formatted text after escaping HTML entities.
		return getLiteralTextFilter().filter(
			logger, dateFormat.format(new Date())
		);
	}

}
