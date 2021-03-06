package org.hy.common.thread;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hy.common.Counter;
import org.hy.common.Date;
import org.hy.common.Help;





/**
 * 任务执行程序
 * 
 * @author      ZhengWei(HY)
 * @createDate  2013-12-16
 * @version     v1.0  
 *              v2.0  2014-07-21：融合XJava、任务池、线程池的功能
 *              v3.0  2015-11-03：是否在初始时(即添加到Jobs时)，就执行一次任务
 *              v4.0  2016-07-08：支持轮询间隔：秒
 */
public final class Jobs extends Job
{
    /**
     * 所有计划任务配置信息
     */
    private List<Job>            jobList;
    
    /** 
     * 正在运行的任务池中的任务运行数量
     * 
     * Key: Job.getCode();
     */
    private Counter<String>      jobMonitor;
    
    /** 最小轮询间隔类型 */
    private int                  minIntervalType;
    
    
    
    public Jobs()
    {
        this.jobList    = new ArrayList<Job>();
        this.jobMonitor = new Counter<String>();
        this.setDesc("Jobs Total scheduling");
    }
    
    
    /**
     * 运行
     */
    public synchronized void startup()
    {
        Help.toSort(this.jobList ,"intervalType");
        
        this.minIntervalType = this.jobList.get(0).getIntervalType();
        
        TaskPool.putTask(this);
    }
    
    
    
    /**
     * 停止。但不会马上停止所有的线程，会等待已运行中的线程运行完成后，才停止。
     */
    public synchronized void shutdown()
    {
        this.finishTask();
    }
    
    
    
    /**
     * 为了方便的XJava的配置文件使用
     * 
     * @param i_Job
     */
    public void setAddJob(Job i_Job)
    {
        this.addJob(i_Job);
    }
    
    
    
    public synchronized void addJob(Job i_Job)
    {
        if ( i_Job == null )
        {
            throw new NullPointerException("Job is null.");
        }
        
        if ( Help.isNull(i_Job.getCode()) )
        {
            throw new NullPointerException("Job.getCode() is null."); 
        }
        
        this.jobList.add(i_Job);
        
        // 是否在初始时(即添加到Jobs时)，就执行一次任务
        if ( i_Job.isInitExecute() )
        {
            if ( i_Job.isAtOnceExecute() )
            {
                i_Job.execute();
            }
            else
            {
                this.executeJob(i_Job);
            }
        }
    }
    
    
    
    public synchronized void delJob(Job i_Job)
    {
        if ( i_Job == null )
        {
            throw new NullPointerException("Job is null.");
        }
        
        if ( Help.isNull(i_Job.getCode()) )
        {
            throw new NullPointerException("Job.getCode() is null."); 
        }
        
        this.jobList.remove(i_Job);
        this.jobMonitor.remove(i_Job.getCode());
    }

    
    
    public Iterator<Job> getJobs()
    {
        return this.jobList.iterator();
    }
    
    
    
    /**
     * 定时触发执行动作的方法
     */
    public void execute()
    {
        // 保证00秒运行
        try
        {
            if ( this.minIntervalType == Job.$IntervalType_Second )
            {
                Thread.sleep(1000);
            }
            else
            {
                Thread.sleep(1000 * (60 - Date.getNowTime().getSeconds()));
            }
        }
        catch (Exception exce)
        {
            exce.printStackTrace();
        }
        
        
        final Date    v_Now  = new Date();
        Iterator<Job> v_Iter = this.jobList.iterator();
        
        if ( this.minIntervalType == Job.$IntervalType_Second )
        {
            while ( v_Iter.hasNext() )
            {
                try
                {
                    Job  v_Job      = v_Iter.next();
                    Date v_NextTime = v_Job.getNextTime(v_Now);
                    
                    if ( v_NextTime.equalsYMDHMS(v_Now) )
                    {
                        this.executeJob(v_Job);
                    }
                }
                catch (Exception exce)
                {
                    System.out.println(exce.getMessage());
                }
            }
        }
        else
        {
            while ( v_Iter.hasNext() )
            {
                try
                {
                    Job  v_Job      = v_Iter.next();
                    Date v_NextTime = v_Job.getNextTime(v_Now);
                    
                    if ( v_NextTime.equalsYMDHM(v_Now) )
                    {
                        this.executeJob(v_Job);
                    }
                }
                catch (Exception exce)
                {
                    System.out.println(exce.getMessage());
                }
            }
        }
    }
    
    
    
    /**
     * 执行任务
     * 
     * @param i_Job
     */
    private void executeJob(Job i_Job)
    {
        i_Job.setMyJobs(this);
        if ( addMonitor(i_Job) )
        {
            TaskPool.putTask(i_Job);
        }
    }
    
    
    
    private boolean addMonitor(Job i_DBT)
    {
        return this.monitor(i_DBT ,1);
    }
    
    
    
    /**
     * 注意：delMonitor()方法及monitor()方法不要加同步锁。否则会出现线程阻塞
     */
    public void delMonitor(Job i_Job)
    {
        this.monitor(i_Job ,-1);
    }
    
    
    
    /**
     * 监控。
     * 
     * 控件任务同时运行的线程数
     * 
     * @param i_Job
     * @param i_Type  1:添加监控   -1:删除监控 
     * @return
     */
    private boolean monitor(Job i_Job ,int i_Type)
    {
        if ( Help.isNull(i_Job.getCode()) )
        {
            return false;
        }
        
        if ( i_Type == 1 )
        {
            if ( this.jobMonitor.containsKey(i_Job.getCode()) )
            {
                Long v_Count = this.jobMonitor.get(i_Job.getCode());
                
                if ( v_Count.longValue() < i_Job.getTaskCount() )
                {
                    this.jobMonitor.put(i_Job.getCode());
                }
                else
                {
                    return false;
                }
            }
            else
            {
                this.jobMonitor.put(i_Job.getCode());
            }
        }
        else
        {
            if ( this.jobMonitor.containsKey(i_Job.getCode()) )
            {
                this.jobMonitor.putMinus(i_Job.getCode());
            }
            else
            {
                return false;
            }
        }
        
        return true;
    }
    
}
