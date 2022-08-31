package com.atguigu.gulimall.test.controller;

import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller
public class RedissonController {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/hello")
    @ResponseBody
    public String hello() {

        // 1、获取一把锁，只要锁的名字一样，就是同一把锁
        RLock lock = redissonClient.getLock("my-lock");

        // 2、加锁
        // 此方法不指定过期时间
        // lock.lock();

        //1）锁的自动续期 测试发现如果某一个业务超长，运行期间会自动给锁续上30s，不用担心业务时间长锁因为过期被删掉的问题
        //2）加锁的业务只要运行完成，就不会给锁续期，即使不手动解锁，锁也会在30s后自动删除，所以在测试中将第一个请求获取锁之后没有解锁就将服务关闭，第二个线程依然可以获取锁

        //**********************************************************************************

        // 指定锁的超时时间
        lock.lock(10, TimeUnit.SECONDS);
        //问题:Lock.lock(1,fimeUnit.SECONDS);在锁时间到了以后，不会自动续期。
        //1、如果我们传递了锁的超时时间，就发送给redis执行脚本，进行占锁，默认超时就是我们指定的时间
        //2、如果我们未指定锁的超时时间，就使用30 * 100【LockWatchdogTimeout看门狗的默认时间】;
        //只要占锁成功，就会启动一个定时任务【重新给锁设置过期时间，新的过期时间就是看门狗的默认时间】,每隔10s都会自
        //internalLockLeaseTime【看门狗时间】/ 3,10s
        //最佳实战
        // lock.Lock(30, TimeUnit.SECONDS）;省掉了整个续期操作,指定过期时间尽量比业务时间长
        try {
            System.out.println("加锁成功，执行业务" + Thread.currentThread().getId());
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 3、解锁
            System.out.println("释放锁" + Thread.currentThread().getId());
            lock.unlock();
        }

        return "hello";

    }

    @GetMapping("/write")
    @ResponseBody
    public String write() {
        RReadWriteLock lock = redissonClient.getReadWriteLock("rw-lock");
        String s = UUID.randomUUID().toString();
        RLock rLock = lock.writeLock();
        try {
            rLock.lock();
            System.out.println("写锁加锁成功。。。" + Thread.currentThread().getName());
            redisTemplate.opsForValue().set("writeValue", s);
            Thread.sleep(30000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
            System.out.println("写锁释放..." + Thread.currentThread().getName());
        }
        return s;
    }


    @GetMapping("/read")
    @ResponseBody
    public String read() {
        RReadWriteLock lock = redissonClient.getReadWriteLock("rw-lock");
        String s = "";
        RLock rLock = lock.readLock();

        try {
            rLock.lock();
            System.out.println("读锁加锁成功..." + Thread.currentThread().getName());
            s = redisTemplate.opsForValue().get("writeValue");
            Thread.sleep(30000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
            System.out.println("读锁释放..." + Thread.currentThread().getName());
        }
        return s;
    }


}
