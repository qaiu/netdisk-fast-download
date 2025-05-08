package cn.qaiu.lz.web.model;

import cn.qaiu.db.ddl.Constraint;
import cn.qaiu.db.ddl.Length;
import cn.qaiu.db.ddl.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 隔空喊话消息
 */
@Data
@Table("t_messages")
public class ShoutMessage {

    private static final long serialVersionUID = 1L;

    @Constraint(autoIncrement= true, notNull = true)
    private Long id;

    @Length(varcharSize = 16)
    @Constraint(notNull = true, uniqueKey = "uk_code")
    private String code; // 6位提取码

    @Length(varcharSize = 4096)
    private String content; // 消息内容

    @Length(varcharSize = 32)
    private String ip; // 发送者IP

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime = new Date(); // 创建时间

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expireTime; // 过期时间

    private Boolean isUsed = false; // 是否已使用
}
