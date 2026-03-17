/* eslint-disable react-hooks/exhaustive-deps */
import React, { useState, useEffect, useRef } from "react";
import { Form, message } from "antd";
import { Dayjs } from "dayjs";
import { ModelDashboardCharts } from "@/components/model-dashboard/ModelDashboardCharts";
import { ModelDashboardFilters } from "@/components/model-dashboard/ModelDashboardFilters";
import { ModelDashboardKpiCards } from "@/components/model-dashboard/ModelDashboardKpiCards";
import { ModelDashboardTables } from "@/components/model-dashboard/ModelDashboardTables";
import { echarts, type ECharts } from "@/lib/echarts";
import slsApi from "../lib/slsApi";
import {
  SlsQueryRequest,
  ModelScenarios,
  QueryInterval,
  ScenarioQueryResponse,
} from "../types/sls";
import {
  formatDatetimeLocal,
  rangePresets,
  getTimeRangeLabel,
  formatNumber,
} from "../utils/dateTimeUtils";
import {
  generateMultiLineChartOption,
  generateLineChartOption,
  generateEmptyChartOption,
} from "../utils/chartUtils";
import type {
  FilterOptionsState,
  KpiDataState,
  TableDataState,
  TableValue,
} from "@/components/model-dashboard/types";

/**
 * 模型监控页面
 */
const ModelDashboard: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [timeRangeLabel, setTimeRangeLabel] = useState("");

  // 过滤选项状态
  const [filterOptions, setFilterOptions] = useState<FilterOptionsState>({
    clusterIds: [],
    apis: [],
    models: [],
    routes: [],
    services: [],
    consumers: [],
  });

  // KPI数据状态
  const [kpiData, setKpiData] = useState<KpiDataState>({
    pv: "-",
    uv: "-",
    fallbackCount: "-",
    inputToken: "-",
    outputToken: "-",
    totalToken: "-",
  });

  // 表格数据状态
  const [tableData, setTableData] = useState<TableDataState>({
    modelToken: [],
    consumerToken: [],
    serviceToken: [],
    errorRequests: [],
    ratelimitedConsumer: [],
    riskLabel: [],
    riskConsumer: [],
  });

  // ECharts实例引用
  const qpsChartRef = useRef<HTMLDivElement>(null);
  const successRateChartRef = useRef<HTMLDivElement>(null);
  const tokenPerSecChartRef = useRef<HTMLDivElement>(null);
  const rtChartRef = useRef<HTMLDivElement>(null);
  const ratelimitedChartRef = useRef<HTMLDivElement>(null);
  const cacheChartRef = useRef<HTMLDivElement>(null);

  const qpsChartInstance = useRef<ECharts | null>(null);
  const successRateChartInstance = useRef<ECharts | null>(null);
  const tokenPerSecChartInstance = useRef<ECharts | null>(null);
  const rtChartInstance = useRef<ECharts | null>(null);
  const ratelimitedChartInstance = useRef<ECharts | null>(null);
  const cacheChartInstance = useRef<ECharts | null>(null);

  // 初始化ECharts实例
  useEffect(() => {
    if (qpsChartRef.current) {
      qpsChartInstance.current = echarts.init(qpsChartRef.current);
    }
    if (successRateChartRef.current) {
      successRateChartInstance.current = echarts.init(
        successRateChartRef.current
      );
    }
    if (tokenPerSecChartRef.current) {
      tokenPerSecChartInstance.current = echarts.init(
        tokenPerSecChartRef.current
      );
    }
    if (rtChartRef.current) {
      rtChartInstance.current = echarts.init(rtChartRef.current);
    }
    if (ratelimitedChartRef.current) {
      ratelimitedChartInstance.current = echarts.init(
        ratelimitedChartRef.current
      );
    }
    if (cacheChartRef.current) {
      cacheChartInstance.current = echarts.init(cacheChartRef.current);
    }

    // 组件卸载时销毁实例
    return () => {
      qpsChartInstance.current?.dispose();
      successRateChartInstance.current?.dispose();
      tokenPerSecChartInstance.current?.dispose();
      rtChartInstance.current?.dispose();
      ratelimitedChartInstance.current?.dispose();
      cacheChartInstance.current?.dispose();
    };
  }, []);

  // 初始化默认值
  useEffect(() => {
    const [start, end] =
      rangePresets.find(p => p.label === "最近1周")?.value || [];
    form.setFieldsValue({
      timeRange: [start, end],
      interval: 15,
    });
    // 自动触发一次查询
    handleQuery();
  }, []);

  // 加载过滤选项
  const loadFilterOptions = async (
    startTime: string,
    endTime: string,
    interval: QueryInterval
  ) => {
    try {
      const options = await slsApi.fetchModelFilterOptions(
        startTime,
        endTime,
        interval
      );
      setFilterOptions({
        clusterIds: options.cluster_id || [],
        apis: options.api || [],
        models: options.model || [],
        routes: options.route || [],
        services: options.service || [],
        consumers: options.consumer || [],
      });
    } catch (error) {
      console.error("加载过滤选项失败:", error);
    }
  };

  // 监听时间范围变化
  const handleTimeRangeChange = (
    dates: [Dayjs | null, Dayjs | null] | null
  ) => {
    if (dates && dates.length === 2 && dates[0] && dates[1]) {
      const [start, end] = dates;
      const interval = form.getFieldValue("interval") || 15;
      loadFilterOptions(
        formatDatetimeLocal(start),
        formatDatetimeLocal(end),
        interval
      );
    }
  };

  // 查询KPI数据
  const queryKpiData = async (
    baseParams: Omit<SlsQueryRequest, "scenario">
  ) => {
    try {
      const kpiScenarios = [
        ModelScenarios.PV,
        ModelScenarios.UV,
        ModelScenarios.FALLBACK_COUNT,
        ModelScenarios.INPUT_TOKEN_TOTAL,
        ModelScenarios.OUTPUT_TOKEN_TOTAL,
        ModelScenarios.TOKEN_TOTAL,
      ];

      const requests = kpiScenarios.map(scenario => ({
        ...baseParams,
        scenario,
      }));

      const responses = await slsApi.batchQueryStatistics(requests);

      const getValue = (response: ScenarioQueryResponse, key: string) => {
        if (response.type === "CARD" && response.stats) {
          const stat = response.stats.find(
            (s: { key: string; value: string }) => s.key === key
          );
          return stat ? formatNumber(stat.value) : "-";
        }
        return "-";
      };

      setKpiData({
        pv: getValue(responses[0], "pv"),
        uv: getValue(responses[1], "uv"),
        fallbackCount: getValue(responses[2], "cnt"),
        inputToken: getValue(responses[3], "input_token"),
        outputToken: getValue(responses[4], "output_token"),
        totalToken: getValue(responses[5], "token"),
      });
    } catch (error) {
      console.error("查询KPI数据失败:", error);
    }
  };

  // 查询图表数据
  const queryChartData = async (
    baseParams: Omit<SlsQueryRequest, "scenario">
  ) => {
    try {
      // QPS趋势图
      const qpsResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: ModelScenarios.QPS_STREAM },
        { ...baseParams, scenario: ModelScenarios.QPS_NORMAL },
        { ...baseParams, scenario: ModelScenarios.QPS_TOTAL },
      ]);

      const qpsSeries = [
        {
          name: "流式QPS",
          dataPoints: qpsResponses[0].timeSeries?.dataPoints || [],
        },
        {
          name: "请求QPS",
          dataPoints: qpsResponses[1].timeSeries?.dataPoints || [],
        },
        {
          name: "总QPS",
          dataPoints: qpsResponses[2].timeSeries?.dataPoints || [],
        },
      ];

      if (qpsChartInstance.current) {
        const option =
          qpsSeries[0].dataPoints.length > 0
            ? generateMultiLineChartOption(qpsSeries)
            : generateEmptyChartOption();
        qpsChartInstance.current.setOption(option, true);
      }

      // 成功率趋势图
      const successRateResponse = await slsApi.queryStatistics({
        ...baseParams,
        scenario: ModelScenarios.SUCCESS_RATE,
      });

      if (successRateChartInstance.current) {
        const dataPoints = successRateResponse.timeSeries?.dataPoints || [];
        const option =
          dataPoints.length > 0
            ? generateLineChartOption(dataPoints, {
                isPercentage: true,
                seriesName: "成功率",
              })
            : generateEmptyChartOption();
        successRateChartInstance.current.setOption(option, true);
      }

      // Token/s趋势图
      const tokenPerSecResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: ModelScenarios.TOKEN_PER_SEC_INPUT },
        { ...baseParams, scenario: ModelScenarios.TOKEN_PER_SEC_OUTPUT },
        { ...baseParams, scenario: ModelScenarios.TOKEN_PER_SEC_TOTAL },
      ]);

      const tokenSeries = [
        {
          name: "输入token/s",
          dataPoints: tokenPerSecResponses[0].timeSeries?.dataPoints || [],
        },
        {
          name: "输出token/s",
          dataPoints: tokenPerSecResponses[1].timeSeries?.dataPoints || [],
        },
        {
          name: "总token/s",
          dataPoints: tokenPerSecResponses[2].timeSeries?.dataPoints || [],
        },
      ];

      if (tokenPerSecChartInstance.current) {
        const option =
          tokenSeries[0].dataPoints.length > 0
            ? generateMultiLineChartOption(tokenSeries)
            : generateEmptyChartOption();
        tokenPerSecChartInstance.current.setOption(option, true);
      }

      // 响应时间趋势图
      const rtResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: ModelScenarios.RT_AVG_TOTAL },
        { ...baseParams, scenario: ModelScenarios.RT_AVG_STREAM },
        { ...baseParams, scenario: ModelScenarios.RT_AVG_NORMAL },
        { ...baseParams, scenario: ModelScenarios.RT_FIRST_TOKEN },
      ]);

      const rtSeries = [
        {
          name: "整体RT",
          dataPoints: rtResponses[0].timeSeries?.dataPoints || [],
        },
        {
          name: "流式RT",
          dataPoints: rtResponses[1].timeSeries?.dataPoints || [],
        },
        {
          name: "非流式RT",
          dataPoints: rtResponses[2].timeSeries?.dataPoints || [],
        },
        {
          name: "首包RT",
          dataPoints: rtResponses[3].timeSeries?.dataPoints || [],
        },
      ];

      if (rtChartInstance.current) {
        const option =
          rtSeries[0].dataPoints.length > 0
            ? generateMultiLineChartOption(rtSeries)
            : generateEmptyChartOption();
        rtChartInstance.current.setOption(option, true);
      }

      // 限流请求趋势图
      const ratelimitedResponse = await slsApi.queryStatistics({
        ...baseParams,
        scenario: ModelScenarios.RATELIMITED_PER_SEC,
      });

      if (ratelimitedChartInstance.current) {
        const dataPoints = ratelimitedResponse.timeSeries?.dataPoints || [];
        const option =
          dataPoints.length > 0
            ? generateLineChartOption(dataPoints, { seriesName: "限流请求数" })
            : generateEmptyChartOption();
        ratelimitedChartInstance.current.setOption(option, true);
      }

      // 缓存命中趋势图
      const cacheResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: ModelScenarios.CACHE_HIT },
        { ...baseParams, scenario: ModelScenarios.CACHE_MISS },
        { ...baseParams, scenario: ModelScenarios.CACHE_SKIP },
      ]);

      const cacheSeries = [
        {
          name: "命中",
          dataPoints: cacheResponses[0].timeSeries?.dataPoints || [],
        },
        {
          name: "未命中",
          dataPoints: cacheResponses[1].timeSeries?.dataPoints || [],
        },
        {
          name: "跳过",
          dataPoints: cacheResponses[2].timeSeries?.dataPoints || [],
        },
      ];

      if (cacheChartInstance.current) {
        const option =
          cacheSeries[0].dataPoints.length > 0
            ? generateMultiLineChartOption(cacheSeries)
            : generateEmptyChartOption();
        cacheChartInstance.current.setOption(option, true);
      }
    } catch (error) {
      console.error("查询图表数据失败:", error);
    }
  };

  // 查询表格数据
  const queryTableData = async (
    baseParams: Omit<SlsQueryRequest, "scenario">
  ) => {
    try {
      const tableScenarios = [
        ModelScenarios.MODEL_TOKEN_TABLE,
        ModelScenarios.CONSUMER_TOKEN_TABLE,
        ModelScenarios.SERVICE_TOKEN_TABLE,
        ModelScenarios.ERROR_REQUESTS_TABLE,
        ModelScenarios.RATELIMITED_CONSUMER_TABLE,
        ModelScenarios.RISK_LABEL_TABLE,
        ModelScenarios.RISK_CONSUMER_TABLE,
      ];

      const requests = tableScenarios.map(scenario => ({
        ...baseParams,
        scenario,
      }));

      const responses = await slsApi.batchQueryStatistics(requests);

      setTableData({
        modelToken: (responses[0].table || []) as Record<string, TableValue>[],
        consumerToken: (responses[1].table || []) as Record<
          string,
          TableValue
        >[],
        serviceToken: (responses[2].table || []) as Record<
          string,
          TableValue
        >[],
        errorRequests: (responses[3].table || []) as Record<
          string,
          TableValue
        >[],
        ratelimitedConsumer: (responses[4].table || []) as Record<
          string,
          TableValue
        >[],
        riskLabel: (responses[5].table || []) as Record<string, TableValue>[],
        riskConsumer: (responses[6].table || []) as Record<
          string,
          TableValue
        >[],
      });
    } catch (error) {
      console.error("查询表格数据失败:", error);
    }
  };

  // 查询按钮处理
  const handleQuery = async () => {
    try {
      await form.validateFields();
      const values = form.getFieldsValue();
      const {
        timeRange,
        interval,
        cluster_id,
        api,
        model,
        route,
        service,
        consumer,
      } = values;

      if (!timeRange || timeRange.length !== 2) {
        message.warning("请选择时间范围");
        return;
      }

      setLoading(true);

      const [startTime, endTime] = timeRange;
      const startTimeStr = formatDatetimeLocal(startTime);
      const endTimeStr = formatDatetimeLocal(endTime);

      // 设置时间范围标签
      setTimeRangeLabel(getTimeRangeLabel(startTimeStr, endTimeStr));

      const baseParams: Omit<SlsQueryRequest, "scenario"> = {
        startTime: startTimeStr,
        endTime: endTimeStr,
        interval: interval || 15,
        bizType: "MODEL_API",
        cluster_id,
        api,
        model,
        route,
        service,
        consumer,
      };

      // 并发查询所有数据
      await Promise.all([
        queryKpiData(baseParams),
        queryChartData(baseParams),
        queryTableData(baseParams),
      ]);

      // 查询成功后刷新过滤选项
      await loadFilterOptions(startTimeStr, endTimeStr, interval || 15);

      message.success("查询成功");
    } catch (error) {
      console.error("查询失败:", error);
    } finally {
      setLoading(false);
    }
  };

  // 重置按钮处理
  const handleReset = () => {
    form.resetFields();
    setTimeRangeLabel("");
    setKpiData({
      pv: "-",
      uv: "-",
      fallbackCount: "-",
      inputToken: "-",
      outputToken: "-",
      totalToken: "-",
    });
    setTableData({
      modelToken: [],
      consumerToken: [],
      serviceToken: [],
      errorRequests: [],
      ratelimitedConsumer: [],
      riskLabel: [],
      riskConsumer: [],
    });

    // 清空图表
    qpsChartInstance.current?.clear();
    successRateChartInstance.current?.clear();
    tokenPerSecChartInstance.current?.clear();
    rtChartInstance.current?.clear();
    ratelimitedChartInstance.current?.clear();
    cacheChartInstance.current?.clear();
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">模型监控</h1>
      <ModelDashboardFilters
        filterOptions={filterOptions}
        form={form}
        loading={loading}
        onQuery={handleQuery}
        onReset={handleReset}
        onTimeRangeChange={handleTimeRangeChange}
      />

      <ModelDashboardKpiCards
        kpiData={kpiData}
        timeRangeLabel={timeRangeLabel}
      />

      <ModelDashboardCharts
        cacheChartRef={cacheChartRef}
        qpsChartRef={qpsChartRef}
        ratelimitedChartRef={ratelimitedChartRef}
        rtChartRef={rtChartRef}
        successRateChartRef={successRateChartRef}
        timeRangeLabel={timeRangeLabel}
        tokenPerSecChartRef={tokenPerSecChartRef}
      />

      <ModelDashboardTables
        tableData={tableData}
        timeRangeLabel={timeRangeLabel}
      />
    </div>
  );
};

export default ModelDashboard;
