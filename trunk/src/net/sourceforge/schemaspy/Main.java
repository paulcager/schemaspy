package net.sourceforge.schemaspy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import net.sourceforge.schemaspy.model.Database;
import net.sourceforge.schemaspy.model.ForeignKeyConstraint;
import net.sourceforge.schemaspy.model.Table;
import net.sourceforge.schemaspy.view.DotFormatter;
import net.sourceforge.schemaspy.view.HtmlAnomaliesFormatter;
import net.sourceforge.schemaspy.view.HtmlConstraintIndexFormatter;
import net.sourceforge.schemaspy.view.HtmlGraphFormatter;
import net.sourceforge.schemaspy.view.HtmlMainIndexFormatter;
import net.sourceforge.schemaspy.view.HtmlTableFormatter;
import net.sourceforge.schemaspy.view.JavaScriptFormatter;
import net.sourceforge.schemaspy.view.StyleSheet;
import net.sourceforge.schemaspy.view.TextFormatter;

public class Main {
    public static void main(String[] argv) {
        try {
            List args = new ArrayList(Arrays.asList(argv)); // can't mod the original
            if (args.size() == 0 || args.remove("-h") || args.remove("-?") || args.remove("?") || args.remove("/?")) {
                dumpUsage(null, false, false);
                System.exit(1);
            }

            if (args.remove("-help")) {
                dumpUsage(null, true, false);
                System.exit(1);
            }

            if (args.remove("-dbhelp")) {
                dumpUsage(null, true, true);
                System.exit(1);
            }

            long start = System.currentTimeMillis();

            // allow '=' in param specs
            args = fixupArgs(args);

            final boolean generateHtml = !args.remove("-nohtml");
            final boolean includeImpliedConstraints = !args.remove("-noimplied");

            File outputDir = new File(getParam(args, "-o", true, false));
            if (!outputDir.isDirectory()) {
                if (!outputDir.mkdir()) {
                    System.err.println("Failed to create directory '" + outputDir + "'");
                    System.exit(2);
                }
            }

            File tablesDir = new File(outputDir, "tables");
            if (generateHtml && !tablesDir.isDirectory()) {
                if (!tablesDir.mkdir()) {
                    System.err.println("Failed to create directory '" + tablesDir + "'");
                    System.exit(2);
                }
            }

            new File(outputDir, "graphs").mkdir();

            String dbType = getParam(args, "-t", false, false);
            if (dbType == null)
                dbType = "ora";
            StringBuffer propertiesLoadedFrom = new StringBuffer();
            Properties properties = getDbProperties(dbType, propertiesLoadedFrom);

            String user = getParam(args, "-u", true, false);
            String password = getParam(args, "-p", false, false);
            String schema = null;
            try {
                schema = getParam(args, "-s", false, true).toUpperCase();
            } catch (Exception schemaNotSpecified) {
                schema = user.toUpperCase();
            }

            String css = getParam(args, "-css", false, false);
            if (css == null)
                css = "schemaSpy.css";

            ConnectionURLBuilder urlBuilder = null;
            try {
                urlBuilder = new ConnectionURLBuilder(dbType, args, properties);
            } catch (IllegalArgumentException badParam) {
                dumpUsage(badParam.getMessage(), false, true);
                System.exit(1);
            }

            String dbName = urlBuilder.getDbName().toUpperCase();

            if (args.size() != 0) {
                System.out.print("Warning: Unrecognized option(s):");
                for (Iterator iter = args.iterator(); iter.hasNext(); ) {
                    System.out.print(" " + iter.next());
                }
                System.out.println();
            }

            if (generateHtml)
                StyleSheet.init(new BufferedReader(getStyleSheet(css)));

            Connection connection = getConnection(user, password, urlBuilder.getConnectionURL(), properties, propertiesLoadedFrom.toString());
            DatabaseMetaData meta = connection.getMetaData();

            if (generateHtml) {
                System.out.println("Connected to " + meta.getDatabaseProductName() + " - " + meta.getDatabaseProductVersion());
                System.out.println();
                System.out.print("Gathering schema details");
            }

            //
            // create the spy
            //
            SchemaSpy spy = new SchemaSpy(connection, meta, dbName, schema, properties);
            Database db = spy.getDatabase();

            LineWriter out;
            Collection tables = new ArrayList(db.getTables());
            tables.addAll(db.getViews());

            if (generateHtml) {
                List constraints = DBAnalyzer.getForeignKeyConstraints(tables);
                System.out.println();
                System.out.print("Writing/graphing results");

                // getting implied constraints has a side-effect of associating the parent/child tables, so don't do it
                // here unless they want that behavior
                List impliedConstraints = null;
                if (includeImpliedConstraints)
                    impliedConstraints = DBAnalyzer.getImpliedConstraints(tables);
                else
                    impliedConstraints = new ArrayList();

                HtmlTableFormatter tableFormatter = new HtmlTableFormatter();
                for (Iterator iter = tables.iterator(); iter.hasNext(); ) {
                    System.out.print('.');
                    Table table = (Table)iter.next();
                    out = new LineWriter(new FileWriter(new File(outputDir, "tables/" + table.getName() + ".html")), 24 * 1024);
                    tableFormatter.write(db, table, outputDir, out);
                    out.close();
                }

                out = new LineWriter(new FileWriter(new File(outputDir, "schemaSpy.css")));
                StyleSheet.write(out);
                out.close();
                out = new LineWriter(new FileWriter(new File(outputDir, "tables/schemaSpy.js")));
                new JavaScriptFormatter().write(out);
                out.close();

                File graphsDir = new File(outputDir, "graphs");
                graphsDir.mkdir();
                String dotBaseFilespec = "relationships_";
                out = new LineWriter(new FileWriter(new File(graphsDir, dotBaseFilespec + ".dot")));
                boolean showRelationships = new DotFormatter().writeRelationships(tables, out) > 0;
                out.close();

                if (showRelationships) {
                    out = new LineWriter(new FileWriter(new File(outputDir, dotBaseFilespec + ".html")));
                    showRelationships = new HtmlGraphFormatter().write(db, graphsDir, dotBaseFilespec, out);
                    out.close();
                }

                dotBaseFilespec = "utilities_";
                out = new LineWriter(new FileWriter(new File(graphsDir, dotBaseFilespec + ".dot")));
                boolean showOrphans = new DotFormatter().writeOrphans(tables, out) > 0;
                out.close();

                if (showOrphans) {
                    out = new LineWriter(new FileWriter(new File(outputDir, dotBaseFilespec + ".html")));
                    showOrphans = new HtmlGraphFormatter().write(db, graphsDir, dotBaseFilespec, out);
                    out.close();
                }

                out = new LineWriter(new FileWriter(new File(outputDir, "index.html")), 64 * 1024);
                HtmlMainIndexFormatter indexFormatter = new HtmlMainIndexFormatter();
                indexFormatter.write(db, tables, showRelationships, showOrphans, out);
                out.close();

                out = new LineWriter(new FileWriter(new File(outputDir, "constraintIndex.html")), 256 * 1024);
                HtmlConstraintIndexFormatter constraintIndexFormatter = new HtmlConstraintIndexFormatter();
                constraintIndexFormatter.write(db, constraints, tables, out);
                out.close();

                new File(outputDir, "anomolies.html").delete(); // oops...mis-spelled it
                out = new LineWriter(new FileWriter(new File(outputDir, "anomalies.html")), 16 * 1024);
                HtmlAnomaliesFormatter anomaliesFormatter = new HtmlAnomaliesFormatter();
                anomaliesFormatter.write(db, tables, impliedConstraints, out);
                out.close();
            }

            List recursiveConstraints = new ArrayList();

            // side effect is that the RI relationships get trashed
            // also populates the recursiveConstraints collection
            List orderedTables = spy.sortTablesByRI(recursiveConstraints);

            out = new LineWriter(new FileWriter(new File(outputDir, "insertionOrder.txt")), 16 * 1024);
            new TextFormatter().write(db, orderedTables, false, out);
            out.close();

            out = new LineWriter(new FileWriter(new File(outputDir, "deletionOrder.txt")), 16 * 1024);
            Collections.reverse(orderedTables);
            new TextFormatter().write(db, orderedTables, false, out);
            out.close();

//            File constraintsFile = new File(outputDir, "removeRecursiveConstraints.sql");
//            constraintsFile.delete();
//            if (!recursiveConstraints.isEmpty()) {
//                out = new LineWriter(new FileWriter(constraintsFile), 4 * 1024);
//                writeRemoveRecursiveConstraintsSql(recursiveConstraints, schema, out);
//                out.close();
//            }
//
//            constraintsFile = new File(outputDir, "restoreRecursiveConstraints.sql");
//            constraintsFile.delete();
//
//            if (!recursiveConstraints.isEmpty()) {
//                out = new LineWriter(new FileWriter(constraintsFile), 4 * 1024);
//                writeRestoreRecursiveConstraintsSql(recursiveConstraints, schema, out);
//                out.close();
//            }

            if (generateHtml) {
                System.out.println();
                System.out.println("Wrote table relationship details to directory '" + outputDir + "' in " + (System.currentTimeMillis() - start) / 1000 + " seconds.");
                System.out.println("Start with " + new File(outputDir, "index.html"));
            }
        } catch (IllegalArgumentException badParam) {
            System.err.println();
            badParam.printStackTrace();
            dumpUsage(badParam.getMessage(), false, false);
        } catch (Exception exc) {
            System.err.println();
            exc.printStackTrace();
        }
    }

    private static Connection getConnection(String user, String password, String connectionURL, Properties properties, String propertiesLoadedFrom) throws SQLException, MalformedURLException {
        System.out.println("Using database properties:");
        System.out.println("    " + propertiesLoadedFrom);

        String driverClass = properties.getProperty("driver");
        String driverPath = properties.getProperty("driverPath");

        List classpath = new ArrayList();
        StringTokenizer tokenizer = new StringTokenizer(driverPath, File.pathSeparator);
        while (tokenizer.hasMoreTokens()) {
            classpath.add(new File(tokenizer.nextToken()).toURL());
        }

        URLClassLoader loader = new URLClassLoader((URL[])classpath.toArray(new URL[0]));
        Driver driver = null;
        try {
            driver = (Driver)Class.forName(driverClass, true, loader).newInstance();

            // have to use deprecated method or we won't see messages generated by older drivers
            //java.sql.DriverManager.setLogStream(System.err);
        } catch (Exception exc) {
            System.err.println();
            System.err.println("Failed to load driver [" + driverClass + "] from classpath " + classpath);
            System.err.println();
            System.err.println("Use -t [databaseType] to specify what drivers to use.");
            System.err.println();
            System.err.println("Or modify one from the jar, put it on your file system and point to it");
            System.err.println("with -t [databasePropertiesFile].");
            System.err.println();
            exc.printStackTrace();
            System.exit(1);
        }

        Properties connectionProperties = new Properties();
        connectionProperties.put("user", user);
        if (password != null)
            connectionProperties.put("password", password);

        Connection connection = null;
        try {
            connection = driver.connect(connectionURL, connectionProperties);
            if (connection == null) {
                System.err.println();
                System.err.println("Wrong kind of driver [" + driverClass + "] to connect to the given database url [" + connectionURL + "]");
                System.err.println();
                System.err.println("Check your .properties file.");
                System.exit(1);
            }
        } catch (UnsatisfiedLinkError badPath) {
            System.err.println();
            System.err.println("Failed to load driver [" + driverClass + "] from classpath " + classpath);
            System.err.println();
            System.err.println("Make sure the reported library (.dll/.lib/.so) from the following line can be");
            System.err.println("found by your PATH (or LIB*PATH) environment variable");
            System.err.println();
            badPath.printStackTrace();
            System.exit(1);
        } catch (Exception exc) {
            System.err.println();
            System.err.println("Failed to connect to database URL [" + connectionURL + "]");
            System.err.println();
            exc.printStackTrace();
            System.exit(1);
        }

        return connection;
    }

    /**
     * Currently very DB2-specific
     * @param recursiveConstraints List
     * @param schema String
     * @param out LineWriter
     * @throws IOException
     */
    private static void writeRemoveRecursiveConstraintsSql(List recursiveConstraints, String schema, LineWriter out) throws IOException {
        for (Iterator iter = recursiveConstraints.iterator(); iter.hasNext(); ) {
            ForeignKeyConstraint constraint = (ForeignKeyConstraint)iter.next();
            out.writeln("ALTER TABLE " + schema + "." + constraint.getChildTable() + " DROP CONSTRAINT " + constraint.getName() + ";");
        }
    }

    /**
     * Currently very DB2-specific
     * @param recursiveConstraints List
     * @param schema String
     * @param out LineWriter
     * @throws IOException
     */
    private static void writeRestoreRecursiveConstraintsSql(List recursiveConstraints, String schema, LineWriter out) throws IOException {
        Map ruleTextMapping = new HashMap();
        ruleTextMapping.put(new Character('C'), "CASCADE");
        ruleTextMapping.put(new Character('A'), "NO ACTION");
        ruleTextMapping.put(new Character('N'), "NO ACTION"); // Oracle
        ruleTextMapping.put(new Character('R'), "RESTRICT");
        ruleTextMapping.put(new Character('S'), "SET NULL");  // Oracle

        for (Iterator iter = recursiveConstraints.iterator(); iter.hasNext(); ) {
            ForeignKeyConstraint constraint = (ForeignKeyConstraint)iter.next();
            out.write("ALTER TABLE \"" + schema + "\".\"" + constraint.getChildTable() + "\" ADD CONSTRAINT \"" + constraint.getName() + "\"");
            StringBuffer buf = new StringBuffer();
            for (Iterator columnIter = constraint.getChildColumns().iterator(); columnIter.hasNext(); ) {
                buf.append("\"");
                buf.append(columnIter.next());
                buf.append("\"");
                if (columnIter.hasNext())
                    buf.append(",");
            }
            out.write(" FOREIGN KEY (" + buf.toString() + ")");
            out.write(" REFERENCES \"" + schema + "\".\"" + constraint.getParentTable() + "\"");
            buf = new StringBuffer();
            for (Iterator columnIter = constraint.getParentColumns().iterator(); columnIter.hasNext(); ) {
                buf.append("\"");
                buf.append(columnIter.next());
                buf.append("\"");
                if (columnIter.hasNext())
                    buf.append(",");
            }
            out.write(" (" + buf.toString() + ")");
            out.write(" ON DELETE ");
            out.write(ruleTextMapping.get(new Character(constraint.getDeleteRule())).toString());
            out.write(" ON UPDATE ");
            out.write(ruleTextMapping.get(new Character(constraint.getUpdateRule())).toString());
            out.writeln(";");
        }
    }

    private static void dumpUsage(String errorMessage, boolean detailed, boolean detailedDb) {
        if (errorMessage != null) {
            System.err.println("*** " + errorMessage + " ***");
        }

        if (detailed) {
            System.out.println("SchemaSpy generates an HTML representation of a database's relationships.");
            System.out.println();
        }

        if (!detailedDb) {
            System.out.println("Usage:");
            System.out.println(" java -jar " + getLoadedFromJar() + " [options]");
            System.out.println("   -t databaseType       defaults to ora (see -dbhelp)");
            System.out.println("   -u user");
            System.out.println("   -p password           defaults to no password");
            System.out.println("   -o outputDirectory");
            System.out.println("   -css styleSheet.css   defaults to schemaSpy.css");
            System.out.println("   -nohtml               defaults to generate html");
            System.out.println("   -noimplied            defaults to generate implied relationships");
            System.out.println("   -help                 detailed help");
            System.out.println("   -dbhelp               display databaseType-specific help");
            System.out.println();
        }

        if (!detailed) {
            System.out.println(" java -jar " + getLoadedFromJar() + " -help to display detailed help");
            System.out.println();
        }

        if (detailedDb) {
            System.out.println("Built-in database types and their required connection parameters:");
            Set datatypes = getBuiltInDatabaseTypes(getLoadedFromJar());
            for (Iterator iter = datatypes.iterator(); iter.hasNext(); ) {
                String dbType = iter.next().toString();
                Properties properties = add(new Properties(), ResourceBundle.getBundle(dbType));
                new ConnectionURLBuilder(dbType, null, properties).dumpUsage();
            }
            System.out.println();
        }

        if (detailed || detailedDb) {
            System.out.println("You can use your own database types by specifying the filespec of a .properties file with -t.");
            System.out.println("Grab one out of " + getLoadedFromJar() + " and modify it to suit your needs.");
            System.out.println();
        }

        if (detailed) {
            System.out.println("Sample usage using the default database type (implied -t ora):");
            System.out.println(" java -jar schemaSpy.jar -db epdb -s sonedba -u devuser -p devuser -o output");
            System.out.println();
        }
    }

    public static String getLoadedFromJar() {
        String classpath = System.getProperty("java.class.path");
        return new StringTokenizer(classpath, File.pathSeparator).nextToken();
    }

    private static String getParam(List args, String paramId, boolean required, boolean dbTypeSpecific) {
        int paramIndex = args.indexOf(paramId);
        if (paramIndex < 0) {
            if (required) {
                dumpUsage("Parameter '" + paramId + "' missing." + (dbTypeSpecific ? "  It is required for this database type." : ""), !dbTypeSpecific, dbTypeSpecific);
                System.exit(1);
            } else {
                return null;
            }
        }
        args.remove(paramIndex);
        String param = args.get(paramIndex).toString();
        args.remove(paramIndex);
        return param;
    }

    /**
     * Allow an equal sign in args...like "-db=dbName"
     *
     * @param args List
     * @return List
     */
    private static List fixupArgs(List args) {
        List expandedArgs = new ArrayList();

        Iterator iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next().toString();
            int indexOfEquals = arg.indexOf('=');
            if (indexOfEquals != -1) {
                expandedArgs.add(arg.substring(0, indexOfEquals));
                expandedArgs.add(arg.substring(indexOfEquals + 1));
            } else {
                expandedArgs.add(arg);
            }
        }

        return expandedArgs;
    }

    private static Properties getDbProperties(String dbType, StringBuffer loadedFrom) throws IOException {
        ResourceBundle bundle = null;

        try {
            File propertiesFile = new File(dbType);
            bundle = new PropertyResourceBundle(new FileInputStream(propertiesFile));
            loadedFrom.append(propertiesFile.getAbsolutePath());
        } catch (FileNotFoundException notFoundOnFilesystemWithoutExtension) {
            try {
                File propertiesFile = new File(dbType + ".properties");
                bundle = new PropertyResourceBundle(new FileInputStream(propertiesFile));
                loadedFrom.append(propertiesFile.getAbsolutePath());
            } catch (FileNotFoundException notFoundOnFilesystemWithExtensionTackedOn) {
                try {
                    bundle = ResourceBundle.getBundle(dbType);
                    loadedFrom.append("[" + getLoadedFromJar() + "]" + File.separator + dbType + ".properties");
                } catch (Exception notInJarWithoutPath) {
                    try {
                        String path = SchemaSpy.class.getPackage().getName() + ".dbTypes." + dbType;
                        path = path.replace('.', '/');
                        bundle = ResourceBundle.getBundle(path);
                        loadedFrom.append("[" + getLoadedFromJar() + "]/" + path + ".properties");
                    } catch (Exception notInJar) {
                        notInJar.printStackTrace();
                        notFoundOnFilesystemWithExtensionTackedOn.printStackTrace();
                        throw notFoundOnFilesystemWithoutExtension;
                    }
                }
            }
        }

        Properties properties;

        try {
            String baseDbType = bundle.getString("extends");
            properties = getDbProperties(baseDbType, new StringBuffer());
        } catch (MissingResourceException doesntExtend) {
            properties = new Properties();
        }

        return add(properties, bundle);
    }

    /**
     * Add the contents of <code>bundle</code> to the specified <code>properties</code>.
     *
     * @param properties Properties
     * @param bundle ResourceBundle
     * @return Properties
     */
    private static Properties add(Properties properties, ResourceBundle bundle) {
        Enumeration enum = bundle.getKeys();
        while (enum.hasMoreElements()) {
            Object key = enum.nextElement();
            properties.put(key, bundle.getObject(key.toString()));
        }

        return properties;
    }

    public static Set getBuiltInDatabaseTypes(String loadedFromJar) {
        Set databaseTypes = new TreeSet();
        JarInputStream jar = null;

        try {
            jar = new JarInputStream(new FileInputStream(loadedFromJar));
            JarEntry entry;

            while ((entry = jar.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                int dotPropsIndex = entryName.indexOf(".properties");
                if (dotPropsIndex != -1)
                    databaseTypes.add(entryName.substring(0, dotPropsIndex));
            }
        } catch (IOException exc) {
        } finally {
            try {
                jar.close();
            } catch (Exception ignore) {}
        }

        return databaseTypes;
    }

    private static Reader getStyleSheet(String cssName) throws IOException {
        File cssFile = new File(cssName);
        if (cssFile.exists())
            return new FileReader(cssFile);
        cssFile = new File(System.getProperty("user.dir"), cssName);
        if (cssFile.exists())
            return new FileReader(cssFile);
        Reader css = new InputStreamReader(StyleSheet.class.getClassLoader().getResourceAsStream(cssName));
        if (css == null)
            throw new IllegalStateException("Unable to find requested style sheet: " + cssName);
        return css;
    }
}