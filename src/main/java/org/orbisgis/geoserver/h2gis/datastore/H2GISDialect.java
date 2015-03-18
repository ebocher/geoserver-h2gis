/*
 * h2gis-gs is an extension to geoserver to connect H2GIS a spatial library 
 * that brings spatial support to the H2 Java database.
 *
 * h2gis-gs  is distributed under GPL 3 license. It is produced by the "Atelier SIG"
 * team of the IRSTV Institute <http://www.irstv.fr/> CNRS FR 2488.
 *
 * Copyright (C) 2014-2015 IRSTV (FR CNRS 2488)
 *
 * h2gis-gs  is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * h2gis-gs  is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * h2gis-gs. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.orbisgis.geoserver.h2gis.datastore;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.factory.Hints;
import org.geotools.jdbc.BasicSQLDialect;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.referencing.CRS;
import org.h2.value.ValueGeometry;
import org.h2gis.utilities.SFSUtilities;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

/**
 * 
 *
 * 
 */
public class H2GISDialect extends BasicSQLDialect {

    final static WKTReader wKTReader = new WKTReader();
    
    //geometry type to class map
    final static Map<String, Class> TYPE_TO_CLASS_MAP = new HashMap<String, Class>() {
        {
            put("GEOMETRY", Geometry.class);
            put("POINT", Point.class);
            put("LINESTRING", LineString.class);
            put("POLYGON", Polygon.class);
            put("MULTIPOINT", MultiPoint.class);
            put("MULTILINESTRING", MultiLineString.class);
            put("MULTIPOLYGON", MultiPolygon.class);
            put("GEOMETRYCOLLECTION", GeometryCollection.class);
        }
    };

    //geometry class to type map
    final static Map<Class, String> CLASS_TO_TYPE_MAP = new HashMap<Class, String>() {
        {
            put(Geometry.class, "GEOMETRY");
            put(Point.class, "POINT");
            put(LineString.class, "LINESTRING");
            put(Polygon.class, "POLYGON");
            put(MultiPoint.class, "MULTIPOINT");
            put(MultiLineString.class, "MULTILINESTRING");
            put(MultiPolygon.class, "MULTIPOLYGON");
            put(GeometryCollection.class, "GEOMETRYCOLLECTION");
            put(LinearRing.class, "LINEARRING");
        }
    };
      
    
    boolean functionEncodingEnabled = false;
    
    
    
    @Override
    public boolean isAggregatedSortSupported(String function) {
       return "distinct".equalsIgnoreCase(function);
    }

    public H2GISDialect(JDBCDataStore dataStore) {
        super(dataStore);
    }
   
        public boolean isFunctionEncodingEnabled() {
        return functionEncodingEnabled;
    }
        
    @Override
    public void initializeConnection(Connection cx) throws SQLException {
        super.initializeConnection(cx);
    }

    @Override
    public boolean includeTable(String schemaName, String tableName,
            Connection cx) throws SQLException {
        if (tableName.equals("geometry_columns")) {
            return false;
        } else if (tableName.startsWith("spatial_ref_sys")) {
            return false;
        } 
        return true;
    }
    

    @Override
    public Geometry decodeGeometryValue(GeometryDescriptor descriptor,
            ResultSet rs, String column, GeometryFactory factory, Connection cx)
            throws IOException, SQLException {
        Geometry geom = ValueGeometry.get(rs.getBytes(column)).getGeometry();
        if (geom == null) {
            return null;
        }
        return geom;
    }    
    

    @Override
    public void encodeGeometryColumn(GeometryDescriptor gatt, String prefix, int srid,
            StringBuffer sql) {
        encodeGeometryColumn(gatt, prefix, srid, null, sql);
    }

    @Override
    public void encodeGeometryColumn(GeometryDescriptor gatt, String prefix, int srid, Hints hints,
            StringBuffer sql) {

        boolean force2D = hints != null && hints.containsKey(Hints.FEATURE_2D)
                && Boolean.TRUE.equals(hints.get(Hints.FEATURE_2D));

        if (force2D) {
            sql.append("ST_AsBinary(ST_Force2D(");
            encodeColumnName(prefix, gatt.getLocalName(), sql);
            sql.append("))");
        } else {
            sql.append("ST_AsBinary(");
            encodeColumnName(prefix, gatt.getLocalName(), sql);
            sql.append(")");
        }        
    }    
    

    @Override
    public void encodeGeometryEnvelope(String tableName, String geometryColumn,
            StringBuffer sql) {
        sql.append("ST_AsText(");
        sql.append("ST_Extent(\"").append(geometryColumn).append("\"::geometry))");
    }    
   

    @Override
    public Envelope decodeGeometryEnvelope(ResultSet rs, int column,
            Connection cx) throws SQLException, IOException {
        try {
            String envelope = rs.getString(column);
            if (envelope != null)
                return wKTReader.read(envelope).getEnvelopeInternal();
            else
                // empty one
                return new Envelope();
        } catch (ParseException e) {
            throw (IOException) new IOException(
                    "Error occurred parsing the bounds WKT").initCause(e);
        }
    }

    @Override
    public Class<?> getMapping(ResultSet columnMetaData, Connection cx)
            throws SQLException {
        
        String typeName = columnMetaData.getString("TYPE_NAME");
        
        if("uuid".equalsIgnoreCase(typeName)) {
            return UUID.class;
        }
        
        //Add a function to H2GIS to return the good geometry class
        String gType = null;
        if ("geometry".equalsIgnoreCase(typeName)) {
            gType = SFSUtilities.getGeometryTypeNameFromCode(columnMetaData.getInt("DATA_TYPE"));
        } else {
            return null;
        }
       
        // decode the type into
        if(gType == null) {
            // it's either a generic geography or geometry not registered in the medatata tables
            return Geometry.class;
        } else {
            Class geometryClass = TYPE_TO_CLASS_MAP.get(gType.toUpperCase());
            if (geometryClass == null) {
                geometryClass = Geometry.class;
            }    
            return geometryClass;
        }
    }   
    
    @Override
    public Integer getGeometrySRID(String schemaName, String tableName,
            String columnName, Connection cx) throws SQLException {

        // first attempt, try with the geometry metadata
        Statement statement = null;
        ResultSet result = null;
        int srid = 0;
        try {
            if (schemaName == null)
                schemaName = "PUBLIC";
                        
            
            // try geometry_columns
            try {
                String sqlStatement = "SELECT SRID FROM GEOMETRY_COLUMNS WHERE " //
                        + "F_TABLE_SCHEMA = '" + schemaName + "' " //
                        + "AND F_TABLE_NAME = '" + tableName + "' " //
                        + "AND F_GEOMETRY_COLUMN = '" + columnName + "'";
    
                LOGGER.log(Level.FINE, "Geometry srid check; {0} ", sqlStatement);
                statement = cx.createStatement();
                result = statement.executeQuery(sqlStatement);
    
                if (result.next()) {
                    srid = result.getInt(1);
                }
            } catch(SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve information about " 
                        + schemaName + "." + tableName + "."  + columnName 
                        + " from the geometry_columns table, checking the first geometry instead", e);
            } finally {
                dataStore.closeSafe(result);
            }
            
            // fall back on inspection of the first geometry, assuming uniform srid (fair assumption
            // an unpredictable srid makes the table un-queriable)
            if(srid == 0) {
                String sqlStatement = "SELECT ST_SRID(\"" + columnName + "\") " +
                               "FROM \"" + schemaName + "\".\"" + tableName + "\" " +
                               "WHERE \"" + columnName + "\" IS NOT NULL " +
                               "LIMIT 1";
                result = statement.executeQuery(sqlStatement);
                if (result.next()) {
                    srid = result.getInt(1);
                }
            }
        } finally {
            dataStore.closeSafe(result);
            dataStore.closeSafe(statement);
        }

        return srid;
    }
    
    @Override
    public int getGeometryDimension(String schemaName, String tableName, String columnName,
            Connection cx) throws SQLException {
     // first attempt, try with the geometry metadata
        Statement statement = null;
        ResultSet result = null;
        int dimension = 0;
        try {
            if (schemaName == null)
                schemaName = "PUBLIC";    
            
            // try geometry_columns
            try {
                String sqlStatement = "SELECT COORD_DIMENSION FROM GEOMETRY_COLUMNS WHERE " //
                        + "F_TABLE_SCHEMA = '" + schemaName + "' " //
                        + "AND F_TABLE_NAME = '" + tableName + "' " //
                        + "AND F_GEOMETRY_COLUMN = '" + columnName + "'";
    
                LOGGER.log(Level.FINE, "Geometry srid check; {0} ", sqlStatement);
                statement = cx.createStatement();
                result = statement.executeQuery(sqlStatement);
    
                if (result.next()) {
                    dimension = result.getInt(1);
                }
            } catch(SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to retrieve information about " 
                        + schemaName + "." + tableName + "."  + columnName 
                        + " from the geometry_columns table, checking the first geometry instead", e);
            } finally {
                dataStore.closeSafe(result);
            }
            
            // fall back on inspection of the first geometry, assuming uniform srid (fair assumption
            // an unpredictable srid makes the table un-queriable)
            if(dimension == 0) {
                String sqlStatement = "SELECT ST_DIMENSION(\"" + columnName + "\") " +
                               "FROM \"" + schemaName + "\".\"" + tableName + "\" " +
                               "WHERE " + columnName + " IS NOT NULL " +
                               "LIMIT 1";
                result = statement.executeQuery(sqlStatement);
                if (result.next()) {
                    dimension = result.getInt(1);
                }
            }
        } finally {
            dataStore.closeSafe(result);
            dataStore.closeSafe(statement);
        }

        return dimension;
    }

    @Override
    public String getSequenceForColumn(String schemaName, String tableName,
            String columnName, Connection cx) throws SQLException {

        String sequenceName = tableName + "_" + columnName + "_SEQUENCE";

        //sequence names have to be upper case to select values from them
        sequenceName = sequenceName.toUpperCase();
        Statement st = cx.createStatement();
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM INFORMATION_SCHEMA.SEQUENCES ");
            sql.append("WHERE SEQUENCE_NAME = '").append(sequenceName).append("'");

            dataStore.getLogger().fine(sql.toString());
            ResultSet rs = st.executeQuery(sql.toString());
            try {
                if (rs.next()) {
                    return sequenceName;
                }
            } finally {
                dataStore.closeSafe(rs);
            }
        } finally {
            dataStore.closeSafe(st);
        }

        return null;
    }

    @Override
    public Object getNextSequenceValue(String schemaName, String sequenceName,
            Connection cx) throws SQLException {
        Statement st = cx.createStatement();
        try {
            String sql = "SELECT nextval('" + sequenceName + "')";

            dataStore.getLogger().fine(sql);
            ResultSet rs = st.executeQuery(sql);
            try {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            } finally {
                dataStore.closeSafe(rs);
            }
        } finally {
            dataStore.closeSafe(st);
        }

        return null;
    }    
    
    @Override
    public Object getLastAutoGeneratedValue(String schemaName, String tableName, String columnName,
            Connection cx) throws SQLException {
        
        Statement st = cx.createStatement();
        try {
            String sql = "SELECT lastval()";
            dataStore.getLogger().fine( sql);
            
            ResultSet rs = st.executeQuery( sql);
            try {
                if ( rs.next() ) {
                    return rs.getLong(1);
                }
            } 
            finally {
                dataStore.closeSafe(rs);
            }
        }
        finally {
            dataStore.closeSafe(st);
        }

        return null;
    }
    
    @Override
    public void registerClassToSqlMappings(Map<Class<?>, Integer> mappings) {
        super.registerClassToSqlMappings(mappings);
        // jdbc metadata for geom columns reports DATA_TYPE=1111=Types.OTHER
        mappings.put(Geometry.class, Types.OTHER);
        mappings.put(UUID.class, Types.OTHER);
    }

    @Override
    public void registerSqlTypeNameToClassMappings(
            Map<String, Class<?>> mappings) {
        super.registerSqlTypeNameToClassMappings(mappings);

        mappings.put("geometry", Geometry.class);
        mappings.put("text", String.class);
        mappings.put("int8", Long.class);
        mappings.put("int4", Integer.class);
        mappings.put("bool", Boolean.class);
        mappings.put("character", String.class);
        mappings.put("float8", Double.class);
        mappings.put("int", Integer.class);
        mappings.put("float4", Float.class);
        mappings.put("int2", Short.class);
        mappings.put("time", Time.class);
        mappings.put("timestamp", Timestamp.class);
        mappings.put("timestamptz", Timestamp.class);
        mappings.put("uuid", UUID.class);
        mappings.put("date", Date.class);
    }
    
    @Override
    public void registerSqlTypeToSqlTypeNameOverrides(
            Map<Integer, String> overrides) {
        overrides.put(Types.VARCHAR, "VARCHAR");
        overrides.put(Types.BOOLEAN, "BOOL");
    }

    @Override
    public String getGeometryTypeName(Integer type) {
        return "geometry";
    }

    @Override
    public void encodePrimaryKey(String column, StringBuffer sql) {
        encodeColumnName(column, sql);
        sql.append(" SERIAL PRIMARY KEY");
    }

    /**
     * Creates GEOMETRY_COLUMN registrations and spatial indexes for all
     * geometry columns
     * @throws java.sql.SQLException
     */
    @Override
    public void postCreateTable(String schemaName,
            SimpleFeatureType featureType, Connection cx) throws SQLException {
        schemaName = schemaName != null ? schemaName : "PUBLIC"; 
        String tableName = featureType.getName().getLocalPart();
        
        Statement st = null;
        try {
            st = cx.createStatement();

            // register all geometry columns in the database
            for (AttributeDescriptor att : featureType
                    .getAttributeDescriptors()) {
                if (att instanceof GeometryDescriptor) {
                    GeometryDescriptor gd = (GeometryDescriptor) att;

                    // lookup or reverse engineer the srid
                    int srid = -1;
                    if (gd.getUserData().get(JDBCDataStore.JDBC_NATIVE_SRID) != null) {
                        srid = (Integer) gd.getUserData().get(
                                JDBCDataStore.JDBC_NATIVE_SRID);
                    } else if (gd.getCoordinateReferenceSystem() != null) {
                        try {
                            Integer result = CRS.lookupEpsgCode(gd
                                    .getCoordinateReferenceSystem(), true);
                            if (result != null)
                                srid = result;
                        } catch (Exception e) {
                            LOGGER.log(Level.FINE, "Error looking up the "
                                    + "epsg code for metadata "
                                    + "insertion, assuming -1", e);
                        }
                    }

                    // setup the dimension according to the geometry hints
                    int dimensions = 2;
                    if(gd.getUserData().get(Hints.COORDINATE_DIMENSION) != null) {
                        dimensions = (Integer) gd.getUserData().get(Hints.COORDINATE_DIMENSION);
                    }

                    // grab the geometry type
                    String geomType = CLASS_TO_TYPE_MAP.get(gd.getType().getBinding());
                    if (geomType == null) {
                        geomType = "GEOMETRY";
                    }

                    String sql = null;
                    //setup the geometry type
                    if (dimensions == 3) {
                        geomType = geomType + "Z";
                    } else if (dimensions > 3) {
                        throw new IllegalArgumentException("H2GIS only supports geometries with 2 and 3 dimensions, current value: " + dimensions);
                    }

                    sql = "ALTER TABLE \"" + schemaName + "\".\"" + tableName + "\" "
                            + "ALTER COLUMN \"" + gd.getLocalName() + "\" "
                            + "TYPE geometry (" + geomType + ", " + srid + ");";

                    LOGGER.fine(sql);
                    st.execute(sql);
                    
                    
                    // add a spatial index to the table
                    sql = 
                    "CREATE SPATIAL INDEX \"spatial_" + tableName // 
                            + "_" + gd.getLocalName().toLowerCase() + "\""// 
                            + " ON " //
                            + "\"" + schemaName + "\"" // 
                            + "." //
                            + "\"" + tableName + "\"" //
                            + " (" //
                            + "\"" + gd.getLocalName() + "\"" //
                            + ")";
                    LOGGER.fine(sql);
                    st.execute(sql);
                }
            }
            if (!cx.getAutoCommit()) {
                cx.commit();
            }
         } finally {
            dataStore.closeSafe(st);
        }
    }
    
    @Override
    public void postDropTable(String schemaName, SimpleFeatureType featureType, Connection cx)
            throws SQLException {
        Statement st = cx.createStatement();
        String tableName = featureType.getTypeName();

        try {
            //remove all the geometry_column entries
            String sql = 
                "DELETE FROM GEOMETRY_COLUMNS"
                    + " WHERE f_table_catalog=''" //
                    + " AND f_table_schema = '" + schemaName + "'" 
                    + " AND f_table_name = '" + tableName + "'";
            LOGGER.fine( sql );
            st.execute( sql );
        }
        finally {
            dataStore.closeSafe(st);
        }
    }

    @Override
    public void encodeGeometryValue(Geometry value, int dimension, int srid, StringBuffer sql)
            throws IOException {
    	if (value == null || value.isEmpty()) {
            sql.append("NULL");
        } else {
            if (value instanceof LinearRing) {
                //postgis does not handle linear rings, convert to just a line string
                value = value.getFactory().createLineString(((LinearRing) value).getCoordinateSequence());
            }            
            WKTWriter writer = new WKTWriter(dimension);
            String wkt = writer.write(value);
            sql.append("ST_GeomFromText('").append(wkt).append("', ").append(srid).append(")");
        }
    }

    @Override
    public FilterToSQL createFilterToSQL() {
        H2GISFilterToSQL sql = new H2GISFilterToSQL(this);
        sql.setFunctionEncodingEnabled(functionEncodingEnabled);
        return sql;
    }
    
    @Override
    public boolean isLimitOffsetSupported() {
        return true;
    }
    
    @Override
    public void applyLimitOffset(StringBuffer sql, int limit, int offset) {
        if(limit >= 0 && limit < Integer.MAX_VALUE) {
            sql.append(" LIMIT ").append(limit);
            if(offset > 0) {
                sql.append(" OFFSET ").append(offset);
            }
        } else if(offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }
    
    @Override
    public void encodeValue(Object value, Class type, StringBuffer sql) {
        if (byte[].class == type) {
            byte[] b = (byte[]) value;
            if (value != null) {
                //encode as hex string
                sql.append("'");
                for (int i = 0; i < b.length; i++) {
                    sql.append(Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1));
                }
                sql.append("'");
            } else {
                sql.append("NULL");
            }
        } else {
            super.encodeValue(value, type, sql);
        }

    }
    
    @Override
    public int getDefaultVarcharSize(){
        return -1;
    }
}
