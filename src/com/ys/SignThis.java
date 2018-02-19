package com.ys;

import com.WacomGSS.STU.Protocol.Capability;
import com.WacomGSS.STU.Protocol.Information;
import com.WacomGSS.STU.Protocol.PenData;
import com.WacomGSS.STU.STUException;
import com.WacomGSS.STU.UsbDevice;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

// Notes:
// There are three coordinate spaces to deal with that are named:
//   tablet: the raw tablet coordinate
//   screen: the tablet LCD screen
//   client: the Form window client area

    public class SignThis extends JFrame {

        private static final long serialVersionUID = 1L;
        private SignatureDialog signatureDialog;
        private float minX = Float.MAX_VALUE;
        private float maxX = Float.MIN_VALUE;
        private float minY = Float.MAX_VALUE;
        private float maxY = Float.MIN_VALUE;


        BufferedImage signatureImage;
        JPanel imagePanel;

        private Point2D.Float tabletToClient(PenData penData, Capability capability, JPanel panel) {
            // Client means the panel coordinates.
            float koef = 2.54F;
            return new Point2D.Float((float) penData.getX()
                    * panel.getWidth() * koef / capability.getTabletMaxX(),
                    (float) penData.getY() * panel.getHeight() * koef
                            / capability.getTabletMaxY());

        }

        private BufferedImage createImage(PenData[] penData, Capability capability, Information information) {
            BufferedImage bi = new BufferedImage(capability.getScreenWidth(), capability.getScreenHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) bi.getGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0, 0, 64, 255));
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));

            if (null != penData) {

                minX = Float.MAX_VALUE;
                maxX = Float.MIN_VALUE;
                minY = Float.MAX_VALUE;
                maxY = Float.MIN_VALUE;

                for (int i = 1; i < penData.length; i++) {
                    PenData p1 = penData[i];
                    if (p1.getSw() != 0) {
                        Point2D.Float pt1 = tabletToClient(penData[i - 1], capability, imagePanel);
                        Point2D.Float pt2 = tabletToClient(penData[i], capability, imagePanel);
                        Shape l = new Line2D.Float(pt1, pt2);
                        g.draw(l);
                        minX = pt1.x < minX ? pt1.x : minX;
                        minX = pt2.x < minX ? pt2.x : minX;
                        maxX = pt1.x > maxX ? pt1.x : maxX;
                        maxX = pt2.x > maxX ? pt2.x : maxX;
                        minY = pt1.y < minY ? pt1.y : minY;
                        minY = pt2.y < minY ? pt2.y : minY;
                        maxY = pt1.y > maxY ? pt1.y : maxY;
                        maxY = pt2.y > maxY ? pt2.y : maxY;
                    }
                }
                minX = minX - 5 < 0 ? 0 : minX - 5;
                minY = minY - 5 < 0 ? 0 : minY - 5;
                maxX = maxX + 5 > capability.getScreenWidth() ? capability.getScreenWidth() : maxX + 5;
                maxY = maxY + 5 > capability.getScreenHeight() ? capability.getScreenHeight() : maxY + 5;
            }
            return bi;
        }

        private BufferedImage getCroppedImage() {
            BufferedImage bufferedImage = signatureImage.getSubimage((int)minX, (int)minY, (int) (maxX - minX), (int) (maxY - minY));
            return bufferedImage;
        }

        private void onGetSignature() {
            try {
                com.WacomGSS.STU.UsbDevice[] usbDevices = UsbDevice.getUsbDevices();
                if (usbDevices != null && usbDevices.length > 0) {
                    signatureDialog = new SignatureDialog(this,
                            usbDevices[0]);
                    signatureDialog.setVisible(true);

                    PenData[] penData = signatureDialog.getPenData();
                    if (penData != null && penData.length > 0) {
                        // collected data!
                        this.signatureImage = createImage(penData,
                                signatureDialog.getCapability(),
                                signatureDialog.getInformation());
                        imagePanel.repaint();
                    }
                    signatureDialog.dispose();


                    File outputfile = new File("C:/data/sign.png");
                    ImageIO.write(getCroppedImage(), "png", outputfile);
                } else {
                    throw new RuntimeException("No USB tablets attached");
                }
            } catch (STUException e) {
                JOptionPane.showMessageDialog(this, e, "Error (STU)",
                        JOptionPane.ERROR_MESSAGE);
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(this, e, "Error (RT)",
                        JOptionPane.ERROR_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e, "Error (IO)",
                        JOptionPane.ERROR_MESSAGE);
            }
        }

        public SignThis() {
            this.setTitle("Образец подписи");
            this.setLayout(new BorderLayout());

            JPanel panel = new JPanel();
            panel.setLayout(new FlowLayout());

            JButton btn = new JButton("Получить образец подписи");
            btn.addActionListener(evt -> onGetSignature());
            panel.add(btn);

            imagePanel = new JPanel() {
                private static final long serialVersionUID = 1L;

                @Override
                public void paintComponent(Graphics gfx) {
                    super.paintComponent(gfx);
                    if (signatureImage != null) {
                        double newHeight = ((double) signatureImage.getHeight()
                                / signatureImage.getWidth())
                                * this.getWidth();
                        Image rescaled = signatureImage.getScaledInstance(
                                this.getWidth(), (int) newHeight,
                                Image.SCALE_AREA_AVERAGING);
                        gfx.drawImage(rescaled, 0,
                                (int) ((this.getHeight() / 2) - (newHeight / 2)),
                                null);
                    }
                }
            };

            imagePanel.setBorder(new TitledBorder(new LineBorder(new Color(0, 0, 0)),
                    "Образец", TitledBorder.LEADING, TitledBorder.TOP, null,
                    Color.BLACK));
            imagePanel.setPreferredSize(new Dimension(300, 200));

            this.add(panel, BorderLayout.NORTH);
            this.add(imagePanel, BorderLayout.SOUTH);
            this.pack();
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }

        private static void runProgram() {
            SignThis sample = new SignThis();
            sample.setVisible(true);
        }

        public static void main(String[] args) {
            EventQueue.invokeLater(() -> runProgram());
        }
    }

