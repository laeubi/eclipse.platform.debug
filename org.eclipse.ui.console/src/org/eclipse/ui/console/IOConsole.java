package org.eclipse.ui.console;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.graphics.Font;
import org.eclipse.ui.internal.console.IOConsolePage;
import org.eclipse.ui.internal.console.IOConsolePartitioner;
import org.eclipse.ui.part.IPageBookViewPage;

/**
 * This class is new and experimental. It will likely be subject to significant change before
 * it is finalized.
 * 
 * @since 3.1
 *
 */
public class IOConsole extends AbstractConsole {
	
//	DONE colored streams
//	DONE prevent input anywhere except the end
//	DONE autoscroll doesn't	
//	DONE viewer actions (cut/copy/paste are free!)
//	DONE wrap/no wrap
//	DONE activate when written to (at StreamLevel)
//	DONE clear output action
//	DONE partitioner should append to StringBuffer and only update doc once?
//	DONE look at ColorManager - not going to use, clients may want to use it, but Console should not be constructing Color
//	DONE Input stream should return EOF and Exceptions when closed
//	DONE DELETE StreamData!
//	DONE IOConsole.setFont()
//  	DONE Stream.setFontStyle()
//	DONE test synchronization - accepting input while doing output, multiple output streams
//	DONE convert to using Document.getLegalLineDelimiters();
//	DONE do I really want console.setInputColor(...)? - removed
//	DONE buffer size - high/low water mark
//		TODO hyperlinks
//		TODO performance needs to be much better
//		TODO hook up ProcessConsole
//		TODO tab size - busted??? seems to be doing the right thing, but tab size isn't what is expected.. Mac bug????
//  		TODO fixed width
//		TODO terminateConsole???  (if terminated/removed from manager, streams should be closed, ie console needs to remember which streams belong to him)
//		TODO find/replace action
//		TODO Scheduling Rule in Partitioner.
    
	/** 
	 * The font used by this console
	 */
	private Font font = null;
	
	/**
	 * Property constant indicating the font of this console has changed. 
	 */
	public static final String P_FONT = ConsolePlugin.getUniqueIdentifier() + "IOConsole.P_FONT"; //$NON-NLS-1$
	
	/**
	 * Property constant indicating that a font style has changed
	 */
	public static final String P_FONT_STYLE = ConsolePlugin.getUniqueIdentifier() + "IOConsole.P_FONT_STYLE"; //$NON-NLS-1$
	
	/**
	 * Property constant indicating the color of a stream has changed. 
	 */
	public static final String P_STREAM_COLOR = ConsolePlugin.getUniqueIdentifier()  + "IOConsole.P_STREAM_COLOR";	 //$NON-NLS-1$
	
	/**
	 * Property constatn indicating the designated color for user input has changed
	 */
	public static final String P_INPUT_COLOR =  ConsolePlugin.getUniqueIdentifier()  + "IOConsole.P_INPUT_COLOR";	 //$NON-NLS-1$
	
	/**
	 * Property constant indicating tab size has changed 
	 */
	public static final String P_TAB_SIZE = ConsolePlugin.getUniqueIdentifier()  + "IOConsole.P_TAB_SIZE";	 //$NON-NLS-1$
	
	/**
	 * Property constant indicating word wrapping has changed
	 */
	public  static final String P_WORD_WRAP = ConsolePlugin.getUniqueIdentifier() + "IOConsole.P_WORD_WRAP"; //$NON-NLS-1$
	
	/**
	 * The default tab size
	 */
	public static final int DEFAULT_TAB_SIZE = 8;
    
    private IOConsolePartitioner partitioner;
    private IOConsoleInputStream inputStream;
    private int tabWidth = DEFAULT_TAB_SIZE;
    private boolean wordWrap;

    public IOConsole(String name, ImageDescriptor imageDescriptor) {
        super(name, imageDescriptor);
        inputStream = new IOConsoleInputStream(this);
        partitioner = new IOConsolePartitioner(inputStream);
        partitioner.connect(new Document());
    }

    public IDocument getDocument() {
        return partitioner.getDocument();
    }
    
    public IPageBookViewPage createPage(IConsoleView view) {
        return new IOConsolePage(this);
    }

    public IOConsoleOutputStream createOutputStream(String streamId) {
        return new IOConsoleOutputStream(streamId, this);
    }
    
    public IOConsoleInputStream getInputStream() {
        return inputStream;
    }

    public IOConsolePartitioner getPartitioner() {
        return partitioner;
    }

	public void setWaterMarks(int low, int high) {
		partitioner.setWaterMarks(low, high);
	}
	
    public void setTabWidth(final int newTabWidth) {
        if (tabWidth != newTabWidth) {
            final int oldTabWidth = tabWidth;
            tabWidth = newTabWidth;
            ConsolePlugin.getStandardDisplay().asyncExec(new Runnable() {
                public void run() {
                    firePropertyChange(IOConsole.this, P_TAB_SIZE, new Integer(oldTabWidth), new Integer(tabWidth));           
                }
            });
        }
    }
    public int getTabWidth() {
        return tabWidth;
    }

    public Font getFont() {
        return font;
    }
    
    public void setFont(Font newFont) {
        if (font == null || !font.equals(newFont)) {
            Font old = font;
            font = newFont;
            firePropertyChange(this, IOConsole.P_FONT, old, font);
        }
    }
    
    public boolean getWordWrap() {
        return wordWrap;
    }

    public void setWordWrap(boolean wrap) {
        if(wordWrap != wrap) {
            wordWrap = wrap;
            firePropertyChange(IOConsole.this, IOConsole.P_WORD_WRAP, new Boolean(!wordWrap), new Boolean(wordWrap));
        }
    }

    public void activate() {
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(this);
    }

    
}