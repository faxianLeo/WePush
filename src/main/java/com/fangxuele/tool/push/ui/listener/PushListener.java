package com.fangxuele.tool.push.ui.listener;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fangxuele.tool.push.logic.PushData;
import com.fangxuele.tool.push.logic.RunPushThread;
import com.fangxuele.tool.push.ui.Init;
import com.fangxuele.tool.push.ui.form.MessageEditForm;
import com.fangxuele.tool.push.ui.form.PushForm;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <pre>
 * 推送tab相关事件监听
 * </pre>
 *
 * @author <a href="https://github.com/rememberber">RememBerBer</a>
 * @since 2017/6/19.
 */
public class PushListener {
    private static final Log logger = LogFactory.get();

    public static ScheduledExecutorService serviceStartAt;

    public static ScheduledExecutorService serviceStartPerDay;

    public static ScheduledExecutorService serviceStartPerWeek;

    public static void addListeners() {
        // 开始按钮事件
        PushForm.pushForm.getPushStartButton().addActionListener((e) -> ThreadUtil.execute(() -> {
            if (checkBeforePush()) {
                int isPush = JOptionPane.showConfirmDialog(PushForm.pushForm.getPushPanel(),
                        "确定开始推送吗？\n\n推送消息：" +
                                MessageEditForm.messageEditForm.getMsgNameField().getText() +
                                "\n推送人数：" + PushData.allUser.size() +
                                "\n\n空跑模式：" +
                                PushForm.pushForm.getDryRunCheckBox().isSelected(), "确认推送？",
                        JOptionPane.YES_NO_OPTION);
                if (isPush == JOptionPane.YES_OPTION) {
                    // 按钮状态
                    PushForm.pushForm.getScheduleRunButton().setEnabled(false);
                    PushForm.pushForm.getPushStartButton().setEnabled(false);
                    PushForm.pushForm.getPushStopButton().setEnabled(true);
                    ThreadUtil.execute(new RunPushThread());
                }
            }
        }));

        // 停止按钮事件
        PushForm.pushForm.getPushStopButton().addActionListener((e) -> {
            if (!PushData.running && PushData.scheduling) {
                PushForm.pushForm.getScheduleDetailLabel().setText("");
                if (serviceStartAt != null) {
                    serviceStartAt.shutdownNow();
                }
                PushForm.pushForm.getPushStartButton().setEnabled(true);
                PushForm.pushForm.getScheduleRunButton().setEnabled(true);
                PushForm.pushForm.getPushStopButton().setText("停止");
                PushForm.pushForm.getPushStopButton().setEnabled(false);
                PushForm.pushForm.getPushStartButton().updateUI();
                PushForm.pushForm.getScheduleRunButton().updateUI();
                PushForm.pushForm.getPushStopButton().updateUI();
                PushData.scheduling = false;
            }

            if (!PushData.running && PushData.fixRateScheduling) {
                PushForm.pushForm.getScheduleDetailLabel().setText("");
                if (serviceStartPerDay != null) {
                    serviceStartPerDay.shutdownNow();
                }
                if (serviceStartPerWeek != null) {
                    serviceStartPerWeek.shutdownNow();
                }
                PushForm.pushForm.getPushStartButton().setEnabled(true);
                PushForm.pushForm.getScheduleRunButton().setEnabled(true);
                PushForm.pushForm.getPushStopButton().setText("停止");
                PushForm.pushForm.getPushStopButton().setEnabled(false);
                PushForm.pushForm.getPushStartButton().updateUI();
                PushForm.pushForm.getScheduleRunButton().updateUI();
                PushForm.pushForm.getPushStopButton().updateUI();
                PushData.fixRateScheduling = false;
            }

            if (PushData.running) {
                int isStop = JOptionPane.showConfirmDialog(PushForm.pushForm.getPushPanel(),
                        "确定停止当前的推送吗？", "确认停止？",
                        JOptionPane.YES_NO_OPTION);
                if (isStop == JOptionPane.YES_OPTION) {
                    PushData.running = false;
                }
            }
        });

        // 按计划执行按钮事件
        PushForm.pushForm.getScheduleRunButton().addActionListener((e -> ThreadUtil.execute(() -> {
            if (checkBeforePush()) {

                // 看是否存在设置的计划任务
                boolean existScheduleTask = false;

                // 定时开始
                if (Init.configer.isRadioStartAt()) {
                    long startAtMills = DateUtil.parse(Init.configer.getTextStartAt(), DatePattern.NORM_DATETIME_PATTERN).getTime();
                    if (startAtMills < System.currentTimeMillis()) {
                        JOptionPane.showMessageDialog(PushForm.pushForm.getPushPanel(), "计划开始推送时间不能小于系统当前时间！\n\n请检查计划任务设置！\n\n", "提示",
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    int isSchedulePush = JOptionPane.showConfirmDialog(PushForm.pushForm.getPushPanel(),
                            "将在" +
                                    Init.configer.getTextStartAt() +
                                    "推送\n\n消息：" +
                                    MessageEditForm.messageEditForm.getMsgNameField().getText() +
                                    "\n\n推送人数：" + PushData.allUser.size() +
                                    "\n\n空跑模式：" +
                                    PushForm.pushForm.getDryRunCheckBox().isSelected(), "确认定时推送？",
                            JOptionPane.YES_NO_OPTION);
                    if (isSchedulePush == JOptionPane.YES_OPTION) {
                        PushData.scheduling = true;
                        // 按钮状态
                        PushForm.pushForm.getScheduleRunButton().setEnabled(false);
                        PushForm.pushForm.getPushStartButton().setEnabled(false);
                        PushForm.pushForm.getPushStopButton().setText("停止计划任务");
                        PushForm.pushForm.getPushStopButton().setEnabled(true);

                        PushForm.pushForm.getScheduleDetailLabel().setText("计划任务执行中：将在" +
                                Init.configer.getTextStartAt() +
                                "开始推送");

                        serviceStartAt = Executors.newSingleThreadScheduledExecutor();
                        serviceStartAt.schedule(new RunPushThread(), startAtMills - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                    }
                    existScheduleTask = true;
                }

                // 每天固定时间开始
                if (Init.configer.isRadioPerDay()) {
                    long startPerDayMills = DateUtil.parse(DateUtil.today() + " " + Init.configer.getTextPerDay(), DatePattern.NORM_DATETIME_PATTERN).getTime();

                    int isSchedulePush = JOptionPane.showConfirmDialog(PushForm.pushForm.getPushPanel(),
                            "将在每天" +
                                    Init.configer.getTextPerDay() +
                                    "推送\n\n消息：" +
                                    MessageEditForm.messageEditForm.getMsgNameField().getText() +
                                    "\n\n推送人数：" + PushData.allUser.size() +
                                    "\n\n空跑模式：" +
                                    PushForm.pushForm.getDryRunCheckBox().isSelected(), "确认定时推送？",
                            JOptionPane.YES_NO_OPTION);
                    if (isSchedulePush == JOptionPane.YES_OPTION) {
                        PushData.fixRateScheduling = true;
                        // 按钮状态
                        PushForm.pushForm.getScheduleRunButton().setEnabled(false);
                        PushForm.pushForm.getPushStartButton().setEnabled(false);
                        PushForm.pushForm.getPushStopButton().setText("停止计划任务");
                        PushForm.pushForm.getPushStopButton().setEnabled(true);

                        PushForm.pushForm.getScheduleDetailLabel().setText("计划任务执行中：将在每天" +
                                Init.configer.getTextPerDay() +
                                "开始推送");

                        serviceStartPerDay = Executors.newSingleThreadScheduledExecutor();
                        long millisBetween = startPerDayMills - System.currentTimeMillis();
                        long delay = millisBetween < 0 ? millisBetween + 24 * 60 * 60 * 1000 : millisBetween;
                        serviceStartPerDay.scheduleAtFixedRate(new RunPushThread(), delay, 24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS);
                    }
                    existScheduleTask = true;
                }

                // 每周固定时间开始
                if (Init.configer.isRadioPerWeek()) {

                    long todaySetMills = DateUtil.parse(DateUtil.today() + " " + Init.configer.getTextPerWeekTime(), DatePattern.NORM_DATETIME_PATTERN).getTime();
                    int dayBetween = getDayOfWeek(Init.configer.getTextPerWeekWeek()) - DateUtil.thisDayOfWeek();
                    long startPerWeekMills = dayBetween < 0 ? (dayBetween + 7) * 24 * 60 * 60 * 1000 : dayBetween * 24 * 60 * 60 * 1000;

                    int isSchedulePush = JOptionPane.showConfirmDialog(PushForm.pushForm.getPushPanel(),
                            "将在每周" + Init.configer.getTextPerWeekWeek() +
                                    Init.configer.getTextPerWeekTime() +
                                    "推送\n\n消息：" +
                                    MessageEditForm.messageEditForm.getMsgNameField().getText() +
                                    "\n\n推送人数：" + PushData.allUser.size() +
                                    "\n\n空跑模式：" +
                                    PushForm.pushForm.getDryRunCheckBox().isSelected(), "确认定时推送？",
                            JOptionPane.YES_NO_OPTION);
                    if (isSchedulePush == JOptionPane.YES_OPTION) {
                        PushData.fixRateScheduling = true;
                        // 按钮状态
                        PushForm.pushForm.getScheduleRunButton().setEnabled(false);
                        PushForm.pushForm.getPushStartButton().setEnabled(false);
                        PushForm.pushForm.getPushStopButton().setText("停止计划任务");
                        PushForm.pushForm.getPushStopButton().setEnabled(true);

                        PushForm.pushForm.getScheduleDetailLabel().setText("计划任务执行中：将在每周" +
                                Init.configer.getTextPerWeekWeek() +
                                Init.configer.getTextPerWeekTime() +
                                "开始推送");

                        serviceStartPerWeek = Executors.newSingleThreadScheduledExecutor();
                        long millisBetween = startPerWeekMills + todaySetMills - System.currentTimeMillis();
                        long delay = millisBetween < 0 ? millisBetween + 7 * 24 * 60 * 60 * 1000 : millisBetween;
                        serviceStartPerWeek.scheduleAtFixedRate(new RunPushThread(), delay, 7 * 24 * 60 * 60 * 1000, TimeUnit.MILLISECONDS);
                    }
                    existScheduleTask = true;
                }

                if (!existScheduleTask) {
                    JOptionPane.showMessageDialog(PushForm.pushForm.getPushPanel(), "请先设置计划任务！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        })));

        // 每页分配用户数失去焦点
        PushForm.pushForm.getMaxThreadPoolTextField().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    if (PushData.allUser != null && PushData.allUser.size() > 0) {
                        refreshPushInfo();
                    }
                } catch (Exception e1) {
                    logger.error(e1);
                } finally {
                    super.focusLost(e);
                }
            }
        });

        // 每页分配用户数键入回车
        PushForm.pushForm.getMaxThreadPoolTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    try {
                        if (PushData.allUser != null && PushData.allUser.size() > 0) {
                            refreshPushInfo();
                        }
                    } catch (Exception e1) {
                        logger.error(e1);
                    } finally {
                        super.keyPressed(e);
                    }
                }
            }
        });

        // 每个线程分配的页数失去焦点
        PushForm.pushForm.getThreadCountTextField().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    if (PushData.allUser != null && PushData.allUser.size() > 0) {
                        refreshPushInfo();
                    }
                } catch (Exception e1) {
                    logger.error(e1);
                } finally {
                    super.focusLost(e);
                }
            }
        });

        // 每个线程分配的页数键入回车
        PushForm.pushForm.getThreadCountTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    try {
                        if (PushData.allUser != null && PushData.allUser.size() > 0) {
                            refreshPushInfo();
                        }
                    } catch (Exception e1) {
                        logger.error(e1);
                    } finally {
                        super.keyPressed(e);
                    }
                }
            }
        });

    }

    static void refreshPushInfo() {
        // 总记录数
        long totalCount = PushData.allUser.size();
        PushForm.pushForm.getPushTotalCountLabel().setText("消息总数：" + totalCount);
        // 每个线程平均分配
        int threadCount = Integer.parseInt(PushForm.pushForm.getThreadCountTextField().getText());
        int perThread = (int) (totalCount / threadCount) + 1;
        PushForm.pushForm.getCountPerThread().setText("每个线程平均分配：" + perThread);
        // 可用处理器核心
        PushForm.pushForm.getAvailableProcessorLabel().setText("可用处理器核心：" + Runtime.getRuntime().availableProcessors());
        // JVM内存占用
        PushForm.pushForm.getJvmMemoryLabel().setText("JVM内存占用：" + Runtime.getRuntime().totalMemory() / 1024 / 1024 + "MB/" + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB");
    }

    /**
     * 推送前检查
     *
     * @return boolean
     */
    private static boolean checkBeforePush() {
        if (PushData.allUser == null || PushData.allUser.size() == 0) {
            JOptionPane.showMessageDialog(PushForm.pushForm.getPushPanel(), "请先准备目标用户！", "提示",
                    JOptionPane.INFORMATION_MESSAGE);

            return false;
        }
        if ("0".equals(PushForm.pushForm.getMaxThreadPoolTextField().getText()) || StringUtils.isEmpty(PushForm.pushForm.getMaxThreadPoolTextField().getText())) {
            JOptionPane.showMessageDialog(PushForm.pushForm.getPushPanel(), "请设置每页分配用户数！", "提示",
                    JOptionPane.INFORMATION_MESSAGE);

            return false;
        }
        if ("0".equals(PushForm.pushForm.getThreadCountTextField().getText()) || StringUtils.isEmpty(PushForm.pushForm.getThreadCountTextField().getText())) {
            JOptionPane.showMessageDialog(PushForm.pushForm.getPushPanel(), "请设置每个线程分配的页数！", "提示",
                    JOptionPane.INFORMATION_MESSAGE);

            return false;
        }
        if (StringUtils.isEmpty(MessageEditForm.messageEditForm.getMsgNameField().getText())) {
            JOptionPane.showMessageDialog(PushForm.pushForm.getPushPanel(), "请先编辑消息！", "提示",
                    JOptionPane.INFORMATION_MESSAGE);

            return false;
        }

        return true;
    }

    private static int getDayOfWeek(String week) {
        int dayOfWeek;
        switch (week) {
            case "一":
                dayOfWeek = 2;
                break;
            case "二":
                dayOfWeek = 3;
                break;
            case "三":
                dayOfWeek = 4;
                break;
            case "四":
                dayOfWeek = 5;
                break;
            case "五":
                dayOfWeek = 6;
                break;
            case "六":
                dayOfWeek = 7;
                break;
            case "日":
                dayOfWeek = 1;
                break;
            default:
                dayOfWeek = 0;
        }
        return dayOfWeek;
    }

}
