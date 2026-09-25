package com.safiwash.app;

import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.ctk.sdk.PosApiHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@CapacitorPlugin(name = "CS30Printer")
public class CS30PrinterPlugin extends Plugin {

    private static final String TAG = "CS30PrinterPlugin";

    /* ================================================================
     *  LAYOUT CONSTANTS
     *  These mirror the 58 mm HTML/CSS receipt template:
     *
     *     .r-center   ->  centreLine()          (centred text)
     *     .r-flex     ->  row()                 (label / value spread)
     *     .r-line     ->  dashed()              (dashed separator)
     *     .r-bold     ->  setBold()             (0x33 zoom emphasis)
     *     font-size:14px -> setLarge()          (24x24 header font)
     * ================================================================ */

    /** Characters per line with the 16x16 body font (58 mm paper). */
    private static final int WIDTH_NORMAL = 32;

    /** Characters per line with the 24x24 header font. */
    private static final int WIDTH_LARGE = 16;

    /**
     * Some CS30Pro firmware builds render zoom 0x33 as "bold + double width".
     * If your unit does, set this to true and bold rows will automatically be
     * laid out on the narrower character grid so they never wrap or clip.
     */
    private static final boolean BOLD_DOUBLES_WIDTH = false;

    /* Font presets: (height, width, zoom) */
    private static final byte FH_NORMAL = 16, FW_NORMAL = 16, ZOOM_NORMAL = 0x00;
    private static final byte FH_BOLD   = 16, FW_BOLD   = 16, ZOOM_BOLD   = 0x33;
    private static final byte FH_LARGE  = 24, FW_LARGE  = 24, ZOOM_LARGE  = 0x33;

    /** Width of the character grid currently loaded into the printer. */
    private int lineWidth = WIDTH_NORMAL;

    // ================================================================
    // ENTRY POINT
    // ================================================================

    @PluginMethod
    public void printReceipt(PluginCall call) {
        Log.d(TAG, "printReceipt called");

        // ------------------------------------------------------------
        // 1. RECEIPT PAYLOAD (unchanged)
        // ------------------------------------------------------------
        JSObject receipt = call.getObject("receipt");
        if (receipt == null) {
            Log.e(TAG, "No receipt data provided");
            call.reject("No receipt data provided");
            return;
        }

        // ------------------------------------------------------------
        // 2. PRINTER INSTANCE
        // ------------------------------------------------------------
        PosApiHelper printer = PosApiHelper.getInstance();
        if (printer == null) {
            Log.e(TAG, "PosApiHelper instance is null");
            call.reject("PosApiHelper instance is null");
            return;
        }

        // ------------------------------------------------------------
        // 3. INITIALISE  (standard receipt mode, medium density)
        //    NOTE: the whole print job runs on one thread and is not
        //    interruptible — block Back / Home / Power and suppress
        //    pop-ups while this method is executing (SDK caution #1).
        // ------------------------------------------------------------
        printer.PrintSetMode(0);          // 0 = standard receipt, 1 = label
        printer.PrintSetGray(2);          // 2 = medium density

        int initRet = printer.PrintInit(2, FH_NORMAL, FW_NORMAL, ZOOM_NORMAL);
        if (initRet != 0) {
            Log.e(TAG, "Printer initialization failed: " + initRet);
            call.reject("Printer initialization failed: " + initRet);
            return;
        }

        // ------------------------------------------------------------
        // 4. STATUS CHECK
        // ------------------------------------------------------------
        int statusRet = printer.PrintCheckStatus();
        if (statusRet != 0) {
            String message = statusMessage(statusRet);
            Log.e(TAG, message);
            call.reject(message);
            return;
        }

        // ------------------------------------------------------------
        // 5. EXTRACT RECEIPT DATA
        // ------------------------------------------------------------
        String station       = clean(receipt.optString("station", "SAFIWASH"));
        String date          = clean(receipt.optString("date", "-"));
        String ticket        = clean(receipt.optString("ticket", "-"));
        String plate         = clean(receipt.optString("plate", "-"));
        String vehicle       = clean(receipt.optString("vehicleType", "-"));
        String washers       = clean(receipt.optString("washers", "-"));
        String paymentMethod = clean(receipt.optString("paymentMethod", "-"));
        int    total         = receipt.optInt("total", 0);

        // ================================================================
        //  HEADER   <div class="r-center r-bold" style="font-size:14px">
        //           <div class="r-center" style="font-size:10px">
        //           <div class="r-line">
        // ================================================================
        printer.PrintStr("\n");

        setLarge(printer);
        printer.PrintStr(centreWrapped(station.toUpperCase()));

        setNormal(printer);
        printer.PrintStr(centreLine("Mobile Smart POS Unit"));
        printer.PrintStr(dashed());

        // ================================================================
        //  META ROWS   <div class="r-flex"><span>Date:</span> …</div>
        //              <div class="r-flex"><span>Receipt #:</span> …</div>
        //              <div class="r-line">
        // ================================================================
        printer.PrintStr(row("Date:", date));
        printer.PrintStr(row("Receipt #:", ticket));
        printer.PrintStr(dashed());

        // ================================================================
        //  VEHICLE BLOCK  <div class="r-flex r-bold">Vehicle …</div>
        //                 <div class="r-flex">Category …</div>
        //                 <div class="r-line">
        // ================================================================
        setBold(printer);
        printer.PrintStr(row("Vehicle:", plate.toUpperCase()));

        setNormal(printer);
        printer.PrintStr(row("Category:", vehicle));
        printer.PrintStr(dashed());

        // ================================================================
        //  SERVICE BREAKDOWN  <div class="r-bold">Standard Wash</div>
        //                     <div class="r-flex r-bold">TOTAL …</div>
        // ================================================================
        try {
            JSONArray services = receipt.getJSONArray("services");
            if (services != null && services.length() > 0) {

                setBold(printer);
                printer.PrintStr(centreLine(services.length() == 1
                        ? "SERVICE"
                        : "SERVICES"));

                setNormal(printer);
                for (int i = 0; i < services.length(); i++) {
                    JSONObject service = services.getJSONObject(i);
                    String name  = clean(service.optString("name", "Service"));
                    int    price = service.optInt("price", 0);

                    printer.PrintStr(row(name, "KES " + formatAmount(price)));
                }
                printer.PrintStr(dashed());
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error reading services JSON array", e);
        }

        // ================================================================
        //  TOTAL + PAYMENT   <div class="r-flex r-bold">TOTAL …</div>
        //                    <div class="r-flex">Paid via …</div>
        //                    <div class="r-line">
        // ================================================================
        setBold(printer);
        printer.PrintStr(row("TOTAL:", "KES " + formatAmount(total)));

        setNormal(printer);
        printer.PrintStr(row("Paid via:", paymentMethod));
        printer.PrintStr(dashed());

        // ================================================================
        //  FOOTER   <div class="r-center" style="font-size:10px">
        //           <div class="r-center r-bold">THANK YOU!</div>
        //           <div class="r-center" style="font-size:9px">
        // ================================================================
        printer.PrintStr(centreLine("Attendant: " + washers));

        setBold(printer);
        printer.PrintStr(centreLine("THANK YOU!"));

        setNormal(printer);
        printer.PrintStr(centreLine("Welcome Again"));

        // ================================================================
        //  BARCODE (kept from the original payload contract)
        // ================================================================
        if (!ticket.isEmpty() && !ticket.equals("-")) {
            printer.PrintStr("\n");
            int barcodeRet = printer.PrintBarcode(ticket, 320, 80, "CODE_128");
            if (barcodeRet != 0) {
                Log.w(TAG, "Barcode printing failed with code: " + barcodeRet);
            }
        }

        // Final paper feed so the receipt tears cleanly
        printer.PrintStr("\n\n\n");

        // ================================================================
        //  EXECUTE PRINT JOB
        // ================================================================
        int startRet = printer.PrintStart();
        if (startRet == 0) {
            Log.d(TAG, "Print job completed successfully");
            call.resolve();
        } else {
            Log.e(TAG, "PrintStart failed with error code: " + startRet);
            call.reject("Printing process failed: " + startRet);
        }
    }

    /* ================================================================
     *  FONT / WIDTH CONTEXT
     * ================================================================ */

    private void setNormal(PosApiHelper printer) {
        printer.PrintSetFont(FH_NORMAL, FW_NORMAL, ZOOM_NORMAL);
        lineWidth = WIDTH_NORMAL;
    }

    private void setBold(PosApiHelper printer) {
        printer.PrintSetFont(FH_BOLD, FW_BOLD, ZOOM_BOLD);
        lineWidth = BOLD_DOUBLES_WIDTH ? WIDTH_LARGE : WIDTH_NORMAL;
    }

    private void setLarge(PosApiHelper printer) {
        printer.PrintSetFont(FH_LARGE, FW_LARGE, ZOOM_LARGE);
        lineWidth = WIDTH_LARGE;
    }

    /* ================================================================
     *  LINE BUILDERS — direct equivalents of the CSS rules
     * ================================================================ */

    /** `.r-flex` — label left, value right, gap stretched to full width. */
    private String row(String label, String value) {
        label = clean(label);
        value = clean(value);

        int width = lineWidth;

        // Keep the label from swallowing the line
        int maxLabel = Math.max(1, (width * 2) / 3);
        if (label.length() > maxLabel) {
            label = label.substring(0, maxLabel);
        }

        int available = width - label.length() - 1;   // reserve 1 space of gap
        if (available < 1) {
            available = 1;
        }
        if (value.length() > available) {
            value = value.substring(0, available);
        }

        int gap = width - label.length() - value.length();
        if (gap < 1) {
            gap = 1;
        }

        return label + spaces(gap) + value + "\n";
    }

    /** `.r-center` — centred on the current character grid. */
    private String centreLine(String text) {
        text = clean(text);

        if (text.length() > lineWidth) {
            text = text.substring(0, lineWidth);
        }

        int padding = lineWidth - text.length();
        int left    = padding / 2;
        int right   = padding - left;

        return spaces(left) + text + spaces(right) + "\n";
    }

    /**
     * `.r-center` for long strings — wraps on word boundaries and centres
     * every resulting line, so long station names never clip.
     */
    private String centreWrapped(String text) {
        StringBuilder out = new StringBuilder();
        for (String part : wrap(clean(text), lineWidth)) {
            out.append(centreLine(part));
        }
        return out.toString();
    }

    /** `.r-line` — CSS `border-top: 1px dashed`, rendered as "- - - -". */
    private String dashed() {
        StringBuilder out = new StringBuilder(lineWidth + 1);
        for (int i = 0; i < lineWidth; i++) {
            out.append(i % 2 == 0 ? '-' : ' ');
        }
        return out.append('\n').toString();
    }

    /* ================================================================
     *  LOW-LEVEL HELPERS
     * ================================================================ */

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        StringBuilder current = new StringBuilder();

        for (String word : text.split("\\s+")) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }

            // Hard-break a single word that is longer than the whole line
            while (current.length() > width) {
                lines.add(current.substring(0, width));
                current.delete(0, width);
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String spaces(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            out.append(' ');
        }
        return out.toString();
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ").replace("\r", " ").trim();
    }

    private String formatAmount(int amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private String statusMessage(int status) {
        switch (status) {
            case -1:
                return "Printer paper is low";
            case -2:
                return "Printer temperature is too high";
            case -3:
                return "Printer battery voltage is too low";
            default:
                return "Printer status error: " + status;
        }
    }
}