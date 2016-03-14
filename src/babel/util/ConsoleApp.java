package babel.util;


import jam.util.IconUtils;

import java.io.IOException;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.swing.WindowConstants;

import beast.app.BEASTVersion;
import beast.app.util.ErrorLogHandler;
import beast.app.util.MessageLogHandler;
import beast.app.util.Utils;

public class ConsoleApp {
	PathSampleConsoleApp consoleApp = null;
	
	
	public ConsoleApp(String nameString, String title) throws IOException {
        Utils.loadUIManager();
        System.setProperty("com.apple.macos.useScreenMenuBar", "true");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.showGrowBox", "true");
        System.setProperty("beast.useWindow", "true");

        int maxErrorCount = 100;
        final javax.swing.Icon icon = IconUtils.getIcon(babel.Stats.class, "ps.png");

        BEASTVersion version = new BEASTVersion();
        
        final String aboutString = "<html><div style=\"font-family:sans-serif;\"><center>" +
                "<div style=\"font-size:12;\"><p>Bayesian Evolutionary Analysis Sampling Trees<br>" +
                "Version " + version.getVersionString() + ", " + version.getDateString() + "</p>" +
                version.getHTMLCredits() +
                "</div></center></div></html>";

        consoleApp = new PathSampleConsoleApp(nameString, aboutString, icon);
		
        consoleApp.setTitle(title);

        // Add a handler to handle warnings and errors. This is a ConsoleHandler
        // so the messages will go to StdOut..
        final Logger logger = Logger.getLogger("beast");

        Handler handler = new MessageLogHandler();
        handler.setFilter(new Filter() {
            @Override
			public boolean isLoggable(final LogRecord record) {
                return record.getLevel().intValue() < Level.SEVERE.intValue();
            }
        });
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);

        handler = new ErrorLogHandler(maxErrorCount);
        handler.setLevel(Level.INFO);
        logger.addHandler(handler);
	}

	
    static class PathSampleConsoleApp extends jam.console.ConsoleApplication {

        public PathSampleConsoleApp(final String nameString, final String aboutString, final javax.swing.Icon icon) throws IOException {
            super(nameString, aboutString, icon, false);
            getDefaultFrame().setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        }

        @Override
		public void doStop() {
            // thread.stop is deprecated so need to send a message to running threads...
        }

        public void setTitle(final String title) {
            getDefaultFrame().setTitle(title);
        }
    }
}
