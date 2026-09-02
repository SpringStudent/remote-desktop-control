package io.github.springstudent.dekstop.client.core;

import io.github.springstudent.dekstop.client.config.ClientConfigStore;
import io.github.springstudent.dekstop.client.config.ConnectionHistory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 连接历史管理弹窗:以表格展示历史(最近连接时间倒序),
 * 备注列双击即可行内改名,选中行回车 / 点「连接」即发起连接,Delete / 「删除选中」删除单条,「清空」删除全部。
 *
 * @author ZhouNing
 * @date 2026/09/02
 **/
public class RemoteHistoryDialog extends JDialog {
    private static final int COL_NOTE = 0;
    private static final int COL_CODE = 1;
    private static final int COL_TIME = 2;

    private final RemoteFrame owner;
    private final ClientConfigStore store;
    private final HistoryTableModel tableModel;
    private final JTable table;

    public RemoteHistoryDialog(RemoteFrame owner, ClientConfigStore store) {
        super(owner, "连接历史", true);
        this.owner = owner;
        this.store = store;
        this.tableModel = new HistoryTableModel();
        this.table = new JTable(tableModel);
        initUi();
        tableModel.setData(store.listHistory());
    }

    private void initUi() {
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(COL_NOTE).setPreferredWidth(150);
        table.getColumnModel().getColumn(COL_CODE).setPreferredWidth(120);
        table.getColumnModel().getColumn(COL_TIME).setPreferredWidth(170);
        table.getTableHeader().setToolTipText("最近连接时间(倒序)");
        bindRowActions();

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel tip = new JLabel("双击「备注」列可直接改名 · 选中后回车连接 · Delete 删除选中");
        tip.setForeground(Color.GRAY);
        content.add(tip, BorderLayout.NORTH);

        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JButton connectButton = new JButton("连接");
        connectButton.addActionListener(e -> connectSelected());
        JButton deleteButton = new JButton("删除选中");
        deleteButton.addActionListener(e -> deleteSelected());
        JButton clearButton = new JButton("清空");
        clearButton.addActionListener(e -> clearAll());
        JButton closeButton = new JButton("关闭");
        closeButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.add(connectButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(closeButton);
        content.add(buttonPanel, BorderLayout.SOUTH);

        this.setContentPane(content);
        this.setSize(520, 320);
        this.setMinimumSize(new Dimension(480, 260));
        this.setLocationRelativeTo(owner);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.getRootPane().setDefaultButton(connectButton);
    }

    /**
     * Enter=连接,Delete=删除。仅生效于非编辑态(编辑时焦点在单元格编辑器内,不触发)。
     */
    private void bindRowActions() {
        InputMap inputMap = table.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = table.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "historyConnect");
        actionMap.put("historyConnect", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                connectSelected();
            }
        });
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "historyDelete");
        actionMap.put("historyDelete", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                deleteSelected();
            }
        });
    }

    private String selectedCode() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return tableModel.rowAt(row).getDeviceCode();
    }

    private void connectSelected() {
        String code = selectedCode();
        if (code == null) {
            JOptionPane.showMessageDialog(this, "请先选中一条历史记录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 先关闭弹窗,再走主窗连接流程(后续会弹密码输入框,避免嵌套模态)
        dispose();
        owner.connectToRemoteDevice(code);
    }

    private void deleteSelected() {
        String code = selectedCode();
        if (code == null) {
            JOptionPane.showMessageDialog(this, "请先选中一条历史记录", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirmed = JOptionPane.showConfirmDialog(this, "确定删除历史记录 " + code + " ?", "删除",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmed == JOptionPane.YES_OPTION) {
            store.removeHistory(code);
            tableModel.setData(store.listHistory());
        }
    }

    private void clearAll() {
        if (tableModel.getRowCount() == 0) {
            return;
        }
        int confirmed = JOptionPane.showConfirmDialog(this, "确定清空全部连接历史?", "清空",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmed == JOptionPane.YES_OPTION) {
            for (ConnectionHistory history : new ArrayList<>(store.listHistory())) {
                store.removeHistory(history.getDeviceCode());
            }
            tableModel.setData(store.listHistory());
        }
    }

    /**
     * 备注 | 设备代码 | 最近连接;仅备注列可编辑(行内改名,即改即存)。
     */
    private class HistoryTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"备注", "设备代码", "最近连接"};
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        private List<ConnectionHistory> data = new ArrayList<>();

        void setData(List<ConnectionHistory> rows) {
            data = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        ConnectionHistory rowAt(int row) {
            return data.get(row);
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == COL_NOTE;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ConnectionHistory history = data.get(rowIndex);
            switch (columnIndex) {
                case COL_NOTE:
                    return history.getNote() == null ? "" : history.getNote();
                case COL_CODE:
                    return history.getDeviceCode();
                case COL_TIME:
                    return timeFormat.format(new Date(history.getLastConnectedAt()));
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex != COL_NOTE) {
                return;
            }
            ConnectionHistory history = data.get(rowIndex);
            String note = value == null ? "" : value.toString().trim();
            store.updateNote(history.getDeviceCode(), note);
            // store.updateNote 改动的是同一引用对象,刷新单元格即可同步显示
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}
