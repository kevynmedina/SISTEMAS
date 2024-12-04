import com.fazecast.jSerialComm.SerialPort;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PIDControllerGUI extends JFrame {
    private JFrame frame;
    private JTextField kpField, kiField, kdField, setpointField;
    private JButton sendButton, connectButton, refreshButton;
    private JComboBox<String> portList;
    private SerialPort serialPort;
    private long time = 0;
    private boolean isConnected = false;
    private long lastUpdateTime = System.currentTimeMillis();
    private final long updateInterval = 15;
    private XYSeries series;

    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                PIDControllerGUI window = new PIDControllerGUI();
                window.frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public PIDControllerGUI() {
        initialize();
    }

    private void initialize() {
        frame = new JFrame();
        frame.setBounds(100, 100, 800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(7, 2));

        JLabel lblKp = new JLabel("Kp:");
        panel.add(lblKp);

        kpField = new JTextField();
        kpField.setText("7.12");
        panel.add(kpField);

        JLabel lblKi = new JLabel("Ki:");
        panel.add(lblKi);

        kiField = new JTextField();
        kiField.setText("0.016");
        panel.add(kiField);

        JLabel lblKd = new JLabel("Kd:");
        panel.add(lblKd);

        kdField = new JTextField();
        kdField.setText("25");
        panel.add(kdField);

        JLabel lblSetpoint = new JLabel("Setpoint:");
        panel.add(lblSetpoint);

        setpointField = new JTextField();
        setpointField.setText("26");
        panel.add(setpointField);
        JLabel lblPort = new JLabel("PUERTOS COM");
        panel.add(lblPort);
        sendButton = new JButton("Enviar");
        sendButton.setEnabled(false);
        panel.add(sendButton);


        portList = new JComboBox<>();
        panel.add(portList);

        listSerialPorts();
        connectButton = new JButton("Conectar");
        panel.add(connectButton);

        refreshButton = new JButton("Refrescar puertos");
        panel.add(refreshButton);

        frame.getContentPane().add(panel, BorderLayout.NORTH);



        // Crear la serie XY para graficar los datos de distancia
        series = new XYSeries("Distancia Sensor");
        XYSeriesCollection dataset = new XYSeriesCollection(series);
        JFreeChart chart = ChartFactory.createXYLineChart(
                "Distancia Sensor", "Tiempo", "Distancia (cm)",
                dataset, PlotOrientation.VERTICAL, true, true, false
        );
        ChartPanel chartPanel = new ChartPanel(chart);
        frame.getContentPane().add(chartPanel, BorderLayout.CENTER);

        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connectToPort();
            }
        });

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendPIDValues();
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                listSerialPorts(); // Actualizar la lista de puertos
            }
        });
    }

    private void listSerialPorts() {
        portList.removeAllItems();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            portList.addItem(port.getSystemPortName());
        }
    }

    private boolean openSerialPort(String portName) {
        serialPort = SerialPort.getCommPort(portName);
        serialPort.setBaudRate(9600);
        return serialPort.openPort();
    }

    private void connectToPort() {
        String selectedPort = (String) portList.getSelectedItem();
        if (selectedPort != null) {
            if (openSerialPort(selectedPort)) {
                isConnected = true;
                sendButton.setEnabled(true);
                connectButton.setEnabled(false);
                JOptionPane.showMessageDialog(frame, "Conectado al puerto: " + selectedPort);
                new Thread(new SerialDataReader()).start();
            } else {
            }
        }
    }

    private void sendPIDValues() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(frame, "Por favor, conecta primero al puerto.");
            return;
        }

        try {
            double kp = Double.parseDouble(kpField.getText());
            double ki = Double.parseDouble(kiField.getText());
            double kd = Double.parseDouble(kdField.getText());
            double setpoint = Double.parseDouble(setpointField.getText());
            // Estos valores se envian al arduino en este formato
            String message = String.format("Kp:%.2f:Ki:%.2f:Kd:%.2f:Setpoint:%.2f#", kp, ki, kd, setpoint);
            // Enviar los valores al Arduino
            serialPort.writeBytes(message.getBytes(), message.length());

        //    JOptionPane.showMessageDialog(frame, "Valores enviados: " + message);
        } catch (NumberFormatException e) {
        //    JOptionPane.showMessageDialog(frame, "Por favor, ingrese valores válidos.");
        }
    }


    class SerialDataReader implements Runnable {
        @Override
        public void run() {
            while (isConnected) {
                byte[] buffer = new byte[1024];
                int bytesRead = serialPort.readBytes(buffer, buffer.length);

                if (bytesRead > 0) {
                    String data = new String(buffer, 0, bytesRead).trim();

                    // Limpiar caracteres
                    data = data.replaceAll("[^\\d.\\n]", "");
                    String[] values = data.split("\\n");

                    for (String value : values) {
                        try {
                            if (!value.isEmpty() && value.matches("[\\d]+(\\.[\\d]+)?")) {
                                // Intentar convertir el valor a un número flotante
                                double parsedValue = Double.parseDouble(value);

                                // Obtener el tiempo actual en milisegundos
                                long currentTime = System.currentTimeMillis();

                                // Verificar si han pasado 15ms desde la última actualización
                                if (currentTime - lastUpdateTime >= updateInterval) {
                                    series.add(time++, parsedValue);
                                }
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Datos no válidos recibidos: " + value);
                        }
                    }
                }

                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
