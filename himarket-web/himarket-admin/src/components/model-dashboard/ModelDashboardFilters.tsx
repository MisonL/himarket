import {
  Button,
  Card,
  Col,
  DatePicker,
  Form,
  Row,
  Select,
  type FormInstance,
} from "antd";
import type { Dayjs } from "dayjs";
import { DATETIME_FORMAT, rangePresets } from "@/utils/dateTimeUtils";
import type { FilterOptionsState } from "./types";

const { RangePicker } = DatePicker;

interface ModelDashboardFiltersProps {
  filterOptions: FilterOptionsState;
  form: FormInstance;
  loading: boolean;
  onQuery: () => void;
  onReset: () => void;
  onTimeRangeChange: (dates: [Dayjs | null, Dayjs | null] | null) => void;
}

function toTagOptions(values: string[]) {
  return values.map(value => ({
    label: value,
    value,
  }));
}

export function ModelDashboardFilters({
  filterOptions,
  form,
  loading,
  onQuery,
  onReset,
  onTimeRangeChange,
}: ModelDashboardFiltersProps) {
  return (
    <Card className="mb-6" title="过滤条件">
      <Form form={form} layout="vertical">
        <Row gutter={16}>
          <Col flex="350px">
            <Form.Item
              name="timeRange"
              label="时间范围"
              rules={[{ required: true, message: "请选择时间范围" }]}
            >
              <RangePicker
                showTime
                format={DATETIME_FORMAT}
                presets={rangePresets}
                onChange={onTimeRangeChange}
                style={{ width: "100%" }}
              />
            </Form.Item>
          </Col>
          <Col flex="180px">
            <Form.Item name="interval" label="查询粒度">
              <Select style={{ width: "100%" }}>
                <Select.Option value={1}>1秒</Select.Option>
                <Select.Option value={15}>15秒</Select.Option>
                <Select.Option value={60}>60秒</Select.Option>
              </Select>
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="cluster_id" label="实例ID">
              <Select
                mode="tags"
                placeholder="请选择"
                style={{ width: "100%" }}
                options={toTagOptions(filterOptions.clusterIds)}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="api" label="API">
              <Select
                mode="tags"
                placeholder="请选择"
                style={{ width: "100%" }}
                options={toTagOptions(filterOptions.apis)}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="model" label="模型">
              <Select
                mode="tags"
                placeholder="请选择"
                style={{ width: "100%" }}
                options={toTagOptions(filterOptions.models)}
              />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="consumer" label="消费者">
              <Select
                mode="tags"
                placeholder="请选择"
                style={{ width: "100%" }}
                options={toTagOptions(filterOptions.consumers)}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="route" label="路由">
              <Select
                mode="tags"
                placeholder="请选择"
                style={{ width: "100%" }}
                options={toTagOptions(filterOptions.routes)}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="service" label="服务">
              <Select
                mode="tags"
                placeholder="请选择"
                style={{ width: "100%" }}
                options={toTagOptions(filterOptions.services)}
              />
            </Form.Item>
          </Col>
        </Row>

        <Row>
          <Col span={24}>
            <Form.Item>
              <Button type="primary" onClick={onQuery} loading={loading}>
                查询
              </Button>
              <Button onClick={onReset} style={{ marginLeft: 8 }}>
                重置
              </Button>
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Card>
  );
}
