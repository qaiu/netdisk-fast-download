package cn.qaiu.vx.core.util;

import cn.qaiu.lz.common.util.SnowflakeIdWorker;
import org.junit.Test;

public class SnowflakeIdWorkerTest {

    @Test
    public void idWorker() {
        final SnowflakeIdWorker idWorker = SnowflakeIdWorker.idWorker();
        for (int i = 0; i < 100; i++) {
            long id = idWorker.nextId();
            System.out.println(Long.toBinaryString(id));
            System.out.println(id);
            System.out.println("------------");
        }
    }

    @Test
    public void idWorkerCluster() {
        final SnowflakeIdWorker snowflakeIdWorkerCluster = SnowflakeIdWorker.idWorkerCluster(0, 1);
        for (int i = 0; i < 100; i++) {
            long id = snowflakeIdWorkerCluster.nextId();
            System.out.println(Long.toBinaryString(id));
            System.out.println(id);
            System.out.println("------------\n");
        }

    }
}
