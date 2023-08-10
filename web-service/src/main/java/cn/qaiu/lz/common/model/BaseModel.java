package cn.qaiu.lz.common.model;

import cn.qaiu.lz.common.util.SnowflakeIdWorker;
import lombok.Data;

import java.util.Date;

@Data
abstract public class BaseModel {
    public static final long serialVersionUID = 1L;


    private String id = String.valueOf(SnowflakeIdWorker.idWorker().nextId());

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

}
