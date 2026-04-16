package com.example.ghddapi.sync.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DataSyncMapper {

    @Select("SELECT column_name, data_type FROM information_schema.columns " +
            "WHERE table_schema = DATABASE() AND table_name = #{tableName}")
    List<Map<String, String>> getTableColumns(@Param("tableName") String tableName);

    @Insert("<script>" +
            "INSERT INTO ${tableName} " +
            "<foreach collection='columnNames' item='col' open='(' separator=',' close=')' >" +
            "${col}" +
            "</foreach> " +
            "VALUES " +
            "<foreach collection='values' item='val' open='(' separator=',' close=')' >" +
            "#{val}" +
            "</foreach>" +
            "</script>")
    void insertData(@Param("tableName") String tableName,
                    @Param("columnNames") List<String> columnNames,
                    @Param("values") List<Object> values);

    @Select("SELECT * FROM ${tableName}")
    List<Map<String, Object>> selectAllData(@Param("tableName") String tableName);
}
