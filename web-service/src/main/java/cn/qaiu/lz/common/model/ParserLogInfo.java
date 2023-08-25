package cn.qaiu.lz.common.model;

import cn.qaiu.db.ddl.Length;
import cn.qaiu.db.ddl.Table;
import cn.qaiu.lz.common.util.SnowflakeIdWorker;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
@Table("t_parser_log_info")
public class ParserLogInfo {
    String id = SnowflakeIdWorker.getStringId();
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS", timezone = "GMT+8")
    Date logTime = new Date();
    String path;
    Integer code;

    @Length(varcharSize = 4096)
    String data;
}
