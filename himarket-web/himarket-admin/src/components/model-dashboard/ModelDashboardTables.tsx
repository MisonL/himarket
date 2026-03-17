import { Card, Col, Row, Table } from "antd";
import { generateTableColumns } from "@/utils/chartUtils";
import type { TableDataState, TableValue } from "./types";

interface TableSection {
  key: keyof TableDataState;
  title: string;
}

interface ModelDashboardTablesProps {
  tableData: TableDataState;
  timeRangeLabel: string;
}

const TABLE_ROWS: TableSection[][] = [
  [
    { key: "modelToken", title: "模型token使用统计" },
    { key: "consumerToken", title: "消费者token使用统计" },
  ],
  [
    { key: "serviceToken", title: "服务token使用统计" },
    { key: "errorRequests", title: "错误请求统计" },
  ],
  [
    { key: "ratelimitedConsumer", title: "限流消费者统计" },
    { key: "riskLabel", title: "风险类型统计" },
    { key: "riskConsumer", title: "风险消费者统计" },
  ],
];

function renderTableCard(
  title: string,
  data: Record<string, TableValue>[],
  timeRangeLabel: string
) {
  return (
    <Card
      title={title}
      extra={
        timeRangeLabel ? (
          <span className="text-xs text-gray-400">{timeRangeLabel}</span>
        ) : null
      }
    >
      <Table
        dataSource={data}
        columns={generateTableColumns(data)}
        pagination={false}
        rowKey={(_, index) => index?.toString() || "0"}
        scroll={{ x: "max-content" }}
        size="small"
      />
    </Card>
  );
}

export function ModelDashboardTables({
  tableData,
  timeRangeLabel,
}: ModelDashboardTablesProps) {
  return (
    <>
      {TABLE_ROWS.map((row, rowIndex) => {
        const span = row.length === 3 ? 8 : 12;
        return (
          <Row gutter={16} className="mb-4" key={rowIndex}>
            {row.map(section => (
              <Col span={span} key={section.key}>
                {renderTableCard(
                  section.title,
                  tableData[section.key],
                  timeRangeLabel
                )}
              </Col>
            ))}
          </Row>
        );
      })}
    </>
  );
}
