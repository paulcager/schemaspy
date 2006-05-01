package net.sourceforge.schemaspy.view;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import net.sourceforge.schemaspy.model.*;
import net.sourceforge.schemaspy.util.HtmlEncoder;
import net.sourceforge.schemaspy.util.LineWriter;

public class HtmlFormatter {
    protected void writeHeader(Database db, Table table, String text, boolean showOrphans, LineWriter out) throws IOException {
        out.writeln("<!DOCTYPE HTML PUBLIC '-//W3C//DTD HTML 4.01 Transitional//EN' 'http://www.w3.org/TR/html4/loose.dtd'>");
        out.writeln("<html>");
        out.writeln("<head>");
        out.write("  <title>SchemaSpy - ");
        out.write(getDescription(db, table, text, false));
        out.writeln("</title>");
        out.write("  <link rel=stylesheet href='");
        if (table != null)
            out.write("../");
        out.writeln("schemaSpy.css' type='text/css'>");
        out.writeln("  <meta HTTP-EQUIV='Content-Type' CONTENT='text/html; charset=ISO-8859-1'>");
        out.writeln("  <SCRIPT LANGUAGE='JavaScript' TYPE='text/javascript' SRC='" + (table == null ? "" : "../") + "schemaSpy.js'></SCRIPT>");
        out.writeln("</head>");
        out.writeln("<body onload='syncOptions()'>");
        writeTableOfContents(showOrphans, out);
        out.writeln("<div class='content' style='clear:both;'>");
        out.writeln("<table width='100%' border='0' cellpadding='0'>");
        out.writeln(" <tr>");
        out.write("  <td class='heading' valign='top'><h1>");
        if (table == null)
            out.write("SchemaSpy Analysis of ");
        out.write(getDescription(db, table, text, true));
        out.write("</h1>");
        if (table != null && table.getComments() != null) {
            out.write("&nbsp;<b>Comments:</b>&nbsp;&nbsp;");
            String comments = table.getComments();
            if (Boolean.getBoolean("encodeComments"))
                for (int i = 0; i < comments.length(); ++i)
                    out.write(HtmlEncoder.encode(comments.charAt(i)));
            else
                out.write(comments);
            out.writeln("<p/>");
        }
        out.writeln("</td>");;
        out.writeln("  <td class='heading' align='right' valign='top' title='John Currier - Creator of Cool Tools'><span class='indent'>Generated by</span><br><span class='indent'><span class='signature'><a href='http://schemaspy.sourceforge.net' target='_blank'>SchemaSpy</a></span></span></td>");
        out.writeln(" </tr>");
        out.writeln("</table>");
    }

    protected void writeGeneratedBy(String connectTime, LineWriter html) throws IOException {
        html.write("<span class='container'>");
        html.write("Generated by <span class='signature'><a href='http://schemaspy.sourceforge.net' target='_blank'>SchemaSpy</a></span> on ");
        html.write(connectTime);
        html.writeln("</span>");
    }

    protected void writeTableOfContents(boolean showOrphans, LineWriter html) throws IOException {
        // don't forget to modify HtmlMultipleSchemasIndexPage with any changes to 'header' or 'headerHolder'
        String path = getPathToRoot();
        // have to use a table to deal with a horizontal scrollbar showing up inappropriately
        html.writeln("<table id='headerHolder' cellspacing='0' cellpadding='0'><tr><td>");
        html.writeln("<div id='header'>");
        html.writeln(" <ul>");
        if (isOneOfMultipleSchemas())
            html.writeln("  <li><a href='" + path + "../index.html' title='All Schemas Evaluated'>Schemas</a></li>");
        html.writeln("  <li" + (isMainIndex() ? " id='current'" : "") + "><a href='" + path + "index.html' title='All tables and views in the schema'>Tables</a></li>");
        html.writeln("  <li" + (isRelationshipsPage() ? " id='current'" : "") + "><a href='" + path + "relationships.html' title='Graphical view of table relationships'>Relationships</a></li>");
        if (showOrphans)
            html.writeln("  <li" + (isOrphansPage() ? " id='current'" : "") + "><a href='" + path + "utilities.html' title='Graphical view of tables with neither parents nor children'>Utility&nbsp;Tables</a></li>");
        html.writeln("  <li" + (isConstraintsPage() ? " id='current'" : "") + "><a href='" + path + "constraints.html' title='Useful for diagnosing error messages that just give constraint name or number'>Constraints</a></li>");
        html.writeln("  <li" + (isAnomaliesPage() ? " id='current'" : "") + "><a href='" + path + "anomalies.html' title=\"Things that aren't quite right\">Anomalies</a></li>");
        html.writeln("  <li" + (isColumnsPage() ? " id='current'" : "") + "><a href='" + path + "columns.html' title=\"All of the columns in the schema\">Columns</a></li>");
        html.writeln("  <li><a href='http://sourceforge.net/donate/index.php?group_id=137197' title='Please help keep SchemaSpy alive' target='_blank'>Donate</a></li>");
        html.writeln(" </ul>");
        html.writeln("</div>");
        html.writeln("</td></tr></table>");
    }

    protected String getDescription(Database db, Table table, String text, boolean hoverHelp) {
        StringBuffer description = new StringBuffer();
        if (table != null) {
            if (table.isView())
                description.append("View ");
            else
                description.append("Table ");
        }
        if (hoverHelp)
            description.append("<span title='Database'>");
        description.append(db.getName());
        if (hoverHelp)
            description.append("</span>");
        if (db.getSchema() != null) {
            description.append('.');
            if (hoverHelp)
                description.append("<span title='Schema'>");
            description.append(db.getSchema());
            if (hoverHelp)
                description.append("</span>");
        }
        if (table != null) {
            description.append('.');
            if (hoverHelp)
                description.append("<span title='Table'>");
            description.append(table.getName());
            if (hoverHelp)
                description.append("</span>");
        }
        if (text != null) {
            description.append(" - ");
            description.append(text);
        }

        return description.toString();
    }

    protected boolean sourceForgeLogoEnabled() {
        // I hate this hack, but I don't want to have to pass this boolean everywhere...
        return Boolean.getBoolean("sourceforgelogo");
    }

    protected void writeLegend(boolean tableDetails, LineWriter out) throws IOException {
        writeLegend(tableDetails, true, out);
    }

    protected void writeLegend(boolean tableDetails, boolean graphDetails, LineWriter out) throws IOException {
        out.writeln(" <table class='legend' border='0'>");
        out.writeln("  <tr>");
        out.writeln("   <td class='dataTable' valign='bottom'>Legend:</td>");
        if (sourceForgeLogoEnabled())
            out.writeln("   <td class='container' align='right' valign='top'><a href='http://sourceforge.net' target='_blank'><img src='http://sourceforge.net/sflogo.php?group_id=137197&amp;type=1' alt='SourceForge.net' border='0' height='31' width='88'></a></td>");
        out.writeln("  </tr>");
        out.writeln("  <tr><td class='container' colspan='2'>");
        out.writeln("   <table class='dataTable' border='1'>");
        out.writeln("    <tbody>");
        out.writeln("    <tr><td class='primaryKey'>Primary key columns</td></tr>");
        out.writeln("    <tr><td class='indexedColumn'>Columns with indexes</td></tr>");
        if (tableDetails)
            out.writeln("    <tr class='impliedRelationship'><td class='detail'><span class='impliedRelationship'>Implied relationships</span></td></tr>");
        // comment this out until I can figure out a clean way to embed image references
        //out.writeln("    <tr><td class='container'>Arrows go from children (foreign keys)" + (tableDetails ? "<br>" : " ") + "to parents (primary keys)</td></tr>");
        if (graphDetails) {
            out.writeln("    <tr><td class='excludedColumn'>Excluded column relationships</td></tr>");
            out.writeln("    <tr class='impliedRelationship'><td class='legendDetail'>Dashed lines show" + (tableDetails ? "<br>" : " ") + "implied relationships</td></tr>");
            out.writeln("    <tr><td class='legendDetail'>&lt; <em>n</em> &gt; number of related tables</td></tr>");
        }
        out.writeln("   </table>");
        out.writeln("  </td></tr>");
        out.writeln(" </table>");
        writeFeedMe(out);
        out.writeln("&nbsp;");
    }

    protected void writeFeedMe(LineWriter html) throws IOException {
        html.write("Please <a href='http://sourceforge.net/donate/index.php?group_id=137197' target='_blank' title='Please help keep SchemaSpy alive'>support</a> this project");
    }

    protected void writeExcludedColumns(Set excludedColumns, LineWriter html) throws IOException {
        if (excludedColumns.size() > 0) {
            html.writeln("<span class='excludedRelationship'>");
            html.writeln("<br>These columns were not evaluated during analysis: ");
            Iterator iter = excludedColumns.iterator();
            while (iter.hasNext()) {
                TableColumn column = (TableColumn)iter.next();
                html.write("<a href=\"" + getPathToRoot() + "tables/");
                html.write(column.getTable().getName());
                html.write(".html\">");
                html.write(column.getTable().getName());
                html.write(".");
                html.write(column.getName());
                html.writeln("</a>&nbsp;");
            }
            html.writeln("</span>");
        }
    }


    protected void writeFooter(LineWriter html) throws IOException {
        html.writeln("</div>");
        html.writeln("</body>");
        html.writeln("</html>");
    }

    /**
     * Override if your output doesn't live in the root directory.
     * If non blank must end with a trailing slash.
     *
     * @return String
     */
    protected String getPathToRoot() {
        return "";
    }

    /**
     * Override and return true if you're the main index page.
     *
     * @return boolean
     */
    protected boolean isMainIndex() {
        return false;
    }

    /**
     * Override and return true if you're the relationships page.
     *
     * @return boolean
     */
    protected boolean isRelationshipsPage() {
        return false;
    }

    /**
     * Override and return true if you're the orphans page.
     *
     * @return boolean
     */
    protected boolean isOrphansPage() {
        return false;
    }

    /**
     * Override and return true if you're the constraints page
     *
     * @return boolean
     */
    protected boolean isConstraintsPage() {
        return false;
    }

    /**
     * Override and return true if you're the anomalies page
     *
     * @return boolean
     */
    protected boolean isAnomaliesPage() {
        return false;
    }

    /**
     * Override and return true if you're the columns page
     *
     * @return boolean
     */
    protected boolean isColumnsPage() {
        return false;
    }

    /**
     * Nasty way of dealing with 'global' variables.
     * Returns true if we're evaluating a bunch of schemas in one go and
     * at this point we're evaluating a specific schema.
     *
     * @return boolean
     */
    protected boolean isOneOfMultipleSchemas() {
        return Boolean.getBoolean("oneofmultipleschemas");
    }
}
