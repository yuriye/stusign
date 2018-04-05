package com.ys;

import com.WacomGSS.STU.ITabletHandler;
import com.WacomGSS.STU.Protocol.*;
import com.WacomGSS.STU.STUException;
import com.WacomGSS.STU.Tablet;
import com.WacomGSS.STU.UsbDevice;

import javax.swing.*;

import java.awt.*;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class SignatureDialog extends JDialog implements ITabletHandler {

    private static final long serialVersionUID = 1L;

    private Tablet tablet;
    private Capability capability;
    private Information information;


    // In order to simulate buttons, we have our own Button class that
    // stores the bounds and event handler.
    // Using an array of these makes it easy to add or remove buttons as
    // desired.
    private static class Button {
        Rectangle bounds; // in Screen coordinates
        String text;
        ActionListener click;

        void performClick() {
            click.actionPerformed(null);
        }
    }

    // The isDown flag is used like this:
    // 0 = up
    // +ve = down, pressed on button number
    // -1 = down, inking
    // -2 = down, ignoring
    private int isDown;

    private List<PenData> penData; // Array of data being stored. This can
    // be subsequently used as desired.

    private Button[] btns; // The array of buttons that we are emulating.

    private JPanel panel;

    private BufferedImage bitmap; // This bitmap that we display on the
    // screen.
    private EncodingMode encodingMode; // How we send the bitmap to the
    // device.
    private byte[] bitmapData; // This is the flattened data of the bitmap
    // that we send to the device.

    private Point2D.Float tabletToClient(PenData penData) {
        // Client means the panel coordinates.
        return new Point2D.Float(
                (float) penData.getX() * this.panel.getWidth() / this.capability.getTabletMaxX(),
                (float) penData.getY() * this.panel.getHeight() / this.capability.getTabletMaxY());
    }

    private Point2D.Float tabletToScreen(PenData penData) {
        // Screen means LCD screen of the tablet.
        return new Point2D.Float(
                (float) penData.getX() * this.capability.getScreenWidth() / this.capability.getTabletMaxX(),
                (float) penData.getY() * this.capability.getScreenHeight() / this.capability.getTabletMaxY());
    }

    private Point clientToScreen(Point pt) {
        // client (window) coordinates to LCD screen coordinates.
        // This is needed for converting mouse coordinates into LCD bitmap
        // coordinates as that's
        // what this application uses as the coordinate space for buttons.
        return new Point(
                Math.round((float) pt.getX() * this.capability.getScreenWidth() / this.panel.getWidth()),
                Math.round((float) pt.getY() * this.capability.getScreenHeight() / this.panel.getHeight()));
    }

    private void pressOkButton() throws STUException {
        this.setVisible(false);
    }

    private void pressClearButton() throws STUException {
        clearScreen();
    }

    private void pressCancelButton() throws STUException {
        this.setVisible(false);
        if (this.penData != null) this.penData = null;
    }

    private void clearScreen() throws STUException {
        this.tablet.writeImage(this.encodingMode, this.bitmapData);

        this.penData.clear();
        this.isDown = 0;
        this.panel.repaint();
    }

    public void dispose() {
        // Ensure that you correctly disconnect from the tablet, otherwise
        // you are
        // likely to get errors when wanting to connect a second time.
        if (this.tablet != null) {
            try {
                this.tablet.setInkingMode(InkingMode.Off);
                this.tablet.setClearScreen();
            } catch (Throwable t) {
            }
            this.tablet.disconnect();
            this.tablet = null;
        }

        super.dispose();
    }

    private void drawCenteredString(Graphics2D gfx, String text, int x,
                                    int y, int width, int height) {
        FontMetrics fm = gfx.getFontMetrics(gfx.getFont());
        int textHeight = fm.getHeight();
        int textWidth = fm.stringWidth(text);

        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - textHeight) / 2 + fm.getAscent();

        gfx.drawString(text, textX, textY);
    }

    private void drawInk(Graphics2D gfx, PenData pd0, PenData pd1) {

        int strokeWidth = Math.round(capability.getScreenHeight() / 100.0F);
        strokeWidth = strokeWidth < 3 ? 3 : strokeWidth;

        gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        gfx.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        gfx.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        gfx.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        gfx.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);

        gfx.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

        Point2D.Float pt0 = tabletToClient(pd0);
        Point2D.Float pt1 = tabletToClient(pd1);

        int tr = 255 * pd0.getPressure() / 1024 + 100;
        tr = tr > 200 ? 255 : tr;
        gfx.setColor(new Color(0, 0, 64, tr));
        Shape l = new Line2D.Float(pt0, pt1);
        gfx.draw(l);
    }

    private void drawInk(Graphics2D gfx) {
        PenData[] pd = new PenData[0];
        pd = this.penData.toArray(pd);
        for (int i = 1; i < pd.length; ++i) {
            if (pd[i - 1].getSw() != 0 && pd[i].getSw() != 0) {
                drawInk(gfx, pd[i - 1], pd[i]);
            }
        }
    }

    // Pass in the device you want to connect to!
    public SignatureDialog(Frame frame, UsbDevice usbDevice) throws STUException, InterruptedException {
        super(frame, true);
        this.setLocation(new Point(0, 0));
        this.setLocationRelativeTo(frame);
        this.panel = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            public void paintComponent(Graphics gfx) {
                super.paintComponent(gfx);
                if (bitmap != null) {
                    Image rescaled = bitmap.getScaledInstance(
                            panel.getWidth(), panel.getHeight(),
                            Image.SCALE_SMOOTH);
                    gfx.drawImage(rescaled, 0, 0, null);
                    drawInk((Graphics2D) gfx);
                }
            }
        };

        this.panel.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                Point pt = clientToScreen(e.getPoint());
                for (Button btn : SignatureDialog.this.btns) {
                    if (btn.bounds.contains(pt)) {
                        btn.performClick();
                        break;
                    }
                }
            }
        });

        this.penData = new ArrayList<PenData>();

        try {
            this.tablet = new Tablet();
            // A more sophisticated applications should cycle for a few
            // times as the connection may only be
            // temporarily unavailable for a second or so.
            // For example, if a background process such as Wacom STU
            // Display
            // is running, this periodically updates a slideshow of images
            // to the device.

            int e = -1;
            for (int i = 0; i < 10; i++) {
                e = tablet.usbConnect(usbDevice, true);
                if (e == 0) {
                    break;
                } else {
                    Thread.sleep(1000);
                }

            }
            if (e != 0) {
                throw new RuntimeException(
                        "Failed to connect to USB tablet, error " + e);
            }
            this.capability = tablet.getCapability();
            this.information = tablet.getInformation();

            // Set the size of the client window to be actual size,
            // based on the reported DPI of the monitor.

            int screenResolution = this.getToolkit().getScreenResolution();

            Dimension d = new Dimension(this.capability.getTabletMaxX()
                    * screenResolution / 2540,
                    this.capability.getTabletMaxY() * screenResolution
                            / 2540);

            this.panel.setPreferredSize(d);
            this.setLayout(new BorderLayout());
            this.setResizable(false);
            this.add(this.panel);
            this.pack();

            this.btns = new Button[3];
            this.btns[0] = new Button();
            this.btns[1] = new Button();
            this.btns[2] = new Button();

            if (this.tablet.getProductId() != UsbDevice.ProductId_300) {
                // Place the buttons across the bottom of the screen.

                int w2 = this.capability.getScreenWidth() / 3;
                int w3 = this.capability.getScreenWidth() / 3;
                int w1 = this.capability.getScreenWidth() - w2 - w3;
                int y = this.capability.getScreenHeight() * 6 / 7;
                int h = this.capability.getScreenHeight() - y;

                btns[0].bounds = new java.awt.Rectangle(0, y, w1, h);
                btns[1].bounds = new java.awt.Rectangle(w1, y, w2, h);
                btns[2].bounds = new java.awt.Rectangle(w1 + w2, y, w3, h);
            } else {
                // The STU-300 is very shallow, so it is better to utilise
                // the buttons to the side of the display instead.

                int x = this.capability.getScreenWidth() * 3 / 4;
                int w = this.capability.getScreenWidth() - x;

                int h2 = this.capability.getScreenHeight() / 3;
                int h3 = this.capability.getScreenHeight() / 3;
                int h1 = this.capability.getScreenHeight() - h2 - h3;

                btns[0].bounds = new java.awt.Rectangle(x, 0, w, h1);
                btns[1].bounds = new java.awt.Rectangle(x, h1, w, h2);
                btns[2].bounds = new java.awt.Rectangle(x, h1 + h2, w, h3);
            }
            btns[0].text = "ГОТОВО";
            btns[1].text = "Стереть";
            btns[2].text = "Отменить";

            btns[0].click = new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    try {
                        pressOkButton();
                    } catch (STUException e) {
                        // e
                    }
                }
            };

            btns[1].click = new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    try {
                        pressClearButton();
                    } catch (STUException e) {
                        // e
                    }
                }
            };

            btns[2].click = new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    try {
                        pressCancelButton();
                    } catch (STUException e) {
                        // e
                    }
                }
            };

            byte encodingFlag = ProtocolHelper.simulateEncodingFlag(
                    this.tablet.getProductId(),
                    this.capability.getEncodingFlag());

            boolean useColor = ProtocolHelper
                    .encodingFlagSupportsColor(encodingFlag);

            // Disable color if the bulk driver isn't installed.
            // This isn't necessary, but uploading colour images with out
            // the driver
            // is very slow.
            useColor = useColor && this.tablet.supportsWrite();

            // Calculate the encodingMode that will be used to update the
            // image
            if (useColor) {
                if (this.tablet.supportsWrite())
                    this.encodingMode = EncodingMode.EncodingMode_16bit_Bulk;
                else
                    this.encodingMode = EncodingMode.EncodingMode_16bit;
            } else {
                this.encodingMode = EncodingMode.EncodingMode_1bit;
            }

            // Size the bitmap to the size of the LCD screen.
            // This application uses the same bitmap for both the screen and
            // client (window).
            // However, at high DPI, this bitmap will be stretch and it
            // would be better to
            // create individual bitmaps for screen and client at native
            // resolutions.
            this.bitmap = new BufferedImage(
                    this.capability.getScreenWidth(),
                    this.capability.getScreenHeight(),
                    BufferedImage.TYPE_INT_RGB);
//            {
            Graphics2D gfx = bitmap.createGraphics();
            gfx.setColor(Color.WHITE);
            gfx.fillRect(0, 0, bitmap.getWidth(), bitmap.getHeight());

            double fontSize = (this.btns[0].bounds.getHeight() / 2.0); // pixels
            gfx.setFont(new Font("Serif", Font.PLAIN, (int) fontSize));

            // Draw the buttons
            for (Button btn : this.btns) {
                if (useColor) {
                    gfx.setColor(Color.LIGHT_GRAY);
                    gfx.fillRect((int) btn.bounds.getX(),
                            (int) btn.bounds.getY(),
                            (int) btn.bounds.getWidth(),
                            (int) btn.bounds.getHeight());
                }
                gfx.setColor(Color.BLACK);
                gfx.drawRect((int) btn.bounds.getX(),
                        (int) btn.bounds.getY(),
                        (int) btn.bounds.getWidth(),
                        (int) btn.bounds.getHeight());
                drawCenteredString(gfx, btn.text,
                        (int) btn.bounds.getX(),
                        (int) btn.bounds.getY(),
                        (int) btn.bounds.getWidth(),
                        (int) btn.bounds.getHeight());
            }

            gfx.dispose();
//            }

            // Now the bitmap has been created, it needs to be converted to
            // device-native
            // format.
            this.bitmapData = ProtocolHelper.flatten(this.bitmap,
                    this.bitmap.getWidth(), this.bitmap.getHeight(),
                    useColor);

            // If you wish to further optimize image transfer, you can
            // compress the image using
            // the Zlib algorithm.
            boolean useZlibCompression = false;

            if (!useColor && useZlibCompression) {
                // m_bitmapData = compress_using_zlib(m_bitmapData); //
                // insert compression here!
                // m_encodingMode = EncodingMode.EncodingMode_1bit_Zlib;
            }

            // Add the delagate that receives pen data.
            this.tablet.addTabletHandler(this);

            // Initialize the screen
            clearScreen();

            // Enable the pen data on the screen (if not already)
            this.tablet.setInkingMode(InkingMode.On);
        } catch (Throwable t) {
            if (this.tablet != null) {
                this.tablet.disconnect();
                this.tablet = null;
            }
            throw t;
        }
    }

    public void onGetReportException(STUException e) {
        JOptionPane.showMessageDialog(this, "Error:" + e,
                "Error (onGetReportException)", JOptionPane.ERROR_MESSAGE);
        this.tablet.disconnect();
        this.tablet = null;
        this.penData = null;
        this.setVisible(false);
    }

    public void onUnhandledReportData(byte[] data) {
    }

    public void onPenData(PenData penData) {

        Point2D pt = tabletToScreen(penData);
        int btn = 0; // will be +ve if the pen is over a button.
        {
            for (int i = 0; i < this.btns.length; ++i) {
                if (this.btns[i].bounds.contains(pt)) {
                    btn = i + 1;
                    break;
                }
            }
        }

        boolean isDown = (penData.getSw() != 0);

        // This code uses a model of four states the pen can be in:
        // down or up, and whether this is the first sample of that state.

        if (isDown) {
            if (this.isDown == 0) {
                // transition to down
                if (btn > 0) {
                    // We have put the pen down on a button.
                    // Track the pen without inking on the client.

                    this.isDown = btn;
                } else {
                    // We have put the pen down somewhere else.
                    // Treat it as part of the signature.

                    this.isDown = -1;
                }
            } else {
                // already down, keep doing what we're doing!
                // draw
                if (!this.penData.isEmpty() && this.isDown == -1) {
                    // Draw a line from the previous down point to this down
                    // point.
                    // This is the simplist thing you can do; a more
                    // sophisticated program
                    // can perform higher quality rendering than this!
                    Graphics2D gfx = (Graphics2D) this.panel.getGraphics();
                    drawInk(gfx, this.penData.get(this.penData.size() - 1),
                            penData);
                    gfx.dispose();
                }

            }

            // The pen is down, store it for use later.
            if (this.isDown == -1)
                this.penData.add(penData);
        } else {
            if (this.isDown != 0) {
                // transition to up
                if (btn > 0) {
                    // The pen is over a button

                    if (btn == this.isDown) {
                        // The pen was pressed down over the same button as
                        // is was lifted now.
                        // Consider that as a click!
                        this.btns[btn - 1].performClick();
                    }
                }
                this.isDown = 0;
            } else {
                // still up
            }

            // Add up data once we have collected some down data.
            if (!this.penData.isEmpty())
                this.penData.add(penData);
        }

    }

    public void onPenDataOption(PenDataOption penDataOption) {
        onPenData(penDataOption);
    }

    public void onPenDataEncrypted(PenDataEncrypted penDataEncrypted) {
        onPenData(penDataEncrypted.getPenData1());
        onPenData(penDataEncrypted.getPenData2());
    }

    public void onPenDataEncryptedOption(
            PenDataEncryptedOption penDataEncryptedOption) {
        onPenData(penDataEncryptedOption.getPenDataOption1());
        onPenData(penDataEncryptedOption.getPenDataOption2());
    }

    public void onPenDataTimeCountSequence(
            PenDataTimeCountSequence penDataTimeCountSequence) {
        onPenData(penDataTimeCountSequence);
    }

    public void onPenDataTimeCountSequenceEncrypted(
            PenDataTimeCountSequenceEncrypted penDataTimeCountSequenceEncrypted) {
        onPenData(penDataTimeCountSequenceEncrypted);
    }

    @Override
    public void onEventDataPinPad(EventDataPinPad eventDataPinPad) {

    }

    @Override
    public void onEventDataKeyPad(EventDataKeyPad eventDataKeyPad) {

    }

    @Override
    public void onEventDataSignature(EventDataSignature eventDataSignature) {

    }

    @Override
    public void onEventDataPinPadEncrypted(EventDataPinPadEncrypted eventDataPinPadEncrypted) {

    }

    @Override
    public void onEventDataKeyPadEncrypted(EventDataKeyPadEncrypted eventDataKeyPadEncrypted) {

    }

    @Override
    public void onEventDataSignatureEncrypted(EventDataSignatureEncrypted eventDataSignatureEncrypted) {

    }

    public void onEncryptionStatus(EncryptionStatus encryptionStatus) {
    }

    public void onDevicePublicKey(DevicePublicKey devicePublicKey) {
    }

    public PenData[] getPenData() {
        if (this.penData != null) {
            PenData[] arrayPenData = new PenData[0];
            return this.penData.toArray(arrayPenData);
        }
        return null;
    }

    public Information getInformation() {
        if (this.penData != null) {
            return this.information;
        }
        return null;
    }

    public Capability getCapability() {
        if (this.penData != null) {
            return this.capability;
        }
        return null;
    }

}

