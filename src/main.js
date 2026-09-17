import CS30Printer from "./CS30Printer.js";

document.addEventListener("DOMContentLoaded", () => {
  // 1. Initialize UI Elements & State
  const state = {
    ticket: "SW-" + Math.floor(1000 + Math.random() * 9000),
    vehicleType: "",
    services: [],
    washers: [],
    plate: "",
    paymentMethod: "",
  };

  document.getElementById("sumTicket").innerText = state.ticket;
  document
    .getElementById("createJobBtn")
    .addEventListener("click", printReceipt);

  // 2. Toast Notification Helper
  function showToast(message, type = "error") {
    const container = document.getElementById("toastContainer");
    if (!container) return;

    const toast = document.createElement("div");

    let bgColor = "bg-rose-600";
    let icon = "⚠️";

    if (type === "success") {
      bgColor = "bg-emerald-600";
      icon = "✓";
    } else if (type === "info") {
      bgColor = "bg-slate-800";
      icon = "ℹ";
    }

    toast.className = `
      pointer-events-auto flex items-center gap-2.5 rounded-xl
      px-4 py-3 text-sm font-medium text-white shadow-lg
      transition-all duration-300 transform translate-y-2 opacity-0
      ${bgColor}
    `;

    toast.innerHTML = `
      <span class="text-base font-bold">${icon}</span>
      <span>${message}</span>
    `;

    container.appendChild(toast);

    setTimeout(() => {
      toast.classList.remove("translate-y-2", "opacity-0");
    }, 20);

    setTimeout(() => {
      toast.classList.add("translate-y-2", "opacity-0");

      setTimeout(() => {
        toast.remove();
      }, 300);
    }, 3500);
  }

  // 3. Check CS30 Printer Plugin
  //
  // Because we imported the plugin directly, we don't need:
  // window.Capacitor?.Plugins?.CS30Printer
  //
  // The plugin object exists even before calling it. Actual native
  // availability is checked when printReceipt() is called.

  if (window.Capacitor?.isNativePlatform?.()) {
    console.log("Capacitor native platform detected.");
    console.log("CS30Printer plugin loaded:", CS30Printer);

    showToast("CS30 Printer ready", "success");
  } else {
    console.log("Running in browser/web mode.");

    showToast("Web mode detected", "info");
  }

  // 4. Vehicle Type Selection Logic
  const vehicleBtns = document.querySelectorAll(".v-btn");

  vehicleBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const wasActive = btn.classList.contains("active");

      vehicleBtns.forEach((b) => b.classList.remove("active"));

      if (wasActive) {
        state.vehicleType = "";
      } else {
        btn.classList.add("active");
        state.vehicleType = btn.getAttribute("data-type");
      }

      updateSummary();
    });
  });

  // 5. Services Selection Logic
  const serviceItems = document.querySelectorAll(".service-item");

  serviceItems.forEach((item) => {
    item.addEventListener("click", () => {
      item.classList.toggle("active");

      state.services = [];

      document
        .querySelectorAll(".service-item.active")
        .forEach((activeItem) => {
          state.services.push({
            name: activeItem.getAttribute("data-name"),
            price: parseInt(activeItem.getAttribute("data-price"), 10),
          });
        });

      updateSummary();
    });
  });

  // 6. Assign Washers Logic
  const washerCards = document.querySelectorAll(".washer-card");

  washerCards.forEach((card) => {
    card.addEventListener("click", () => {
      card.classList.toggle("active");

      state.washers = [];

      document.querySelectorAll(".washer-card.active").forEach((activeCard) => {
        state.washers.push(activeCard.getAttribute("data-washer"));
      });

      updateSummary();
    });
  });

  // 7. Plate Input & Payment Method Logic
  const plateInput = document.getElementById("plateNumberInput");

  plateInput.addEventListener("input", (e) => {
    e.target.value = e.target.value.toUpperCase();
    state.plate = e.target.value;
  });

  const payBtns = document.querySelectorAll(".pay-btn");

  payBtns.forEach((btn) => {
    btn.addEventListener("click", () => {
      const wasActive = btn.classList.contains("active");

      payBtns.forEach((b) => b.classList.remove("active"));

      if (wasActive) {
        state.paymentMethod = "";
      } else {
        btn.classList.add("active");
        state.paymentMethod = btn.getAttribute("data-pay");
      }

      updateSummary();
    });
  });

  // 8. Calculation & Summary Update
  function updateSummary() {
    const totalPrice = state.services.reduce(
      (acc, curr) => acc + curr.price,
      0,
    );

    const commissionPerWasher = Math.round(totalPrice * 0.12);

    document.getElementById("sumVehicle").innerText = state.vehicleType || "—";

    document.getElementById("sumServicesCount").innerText =
      state.services.length;

    document.getElementById("sumWashersCount").innerText = state.washers.length;

    document.getElementById("sumPayment").innerText =
      state.paymentMethod || "—";

    document.getElementById("sumTotal").innerText = `KSh ${totalPrice}`;

    document.getElementById("sumCommission").innerText =
      `KSh ${commissionPerWasher}`;

    document.getElementById("createJobBtn").innerText =
      `Create Job — KSh ${totalPrice}`;
  }

  // 9. Print Receipt
  async function printReceipt() {
    const plate = plateInput.value.trim();

    // -----------------------------
    // Validation
    // -----------------------------

    if (!state.vehicleType) {
      showToast("Please select a vehicle type.", "error");
      return;
    }

    if (state.services.length === 0) {
      showToast("Please select at least one service.", "error");
      return;
    }

    if (state.washers.length === 0) {
      showToast("Please assign at least one washer.", "error");
      return;
    }

    if (!plate) {
      showToast("Please enter a vehicle plate number.", "error");
      plateInput.focus();
      return;
    }

    if (!state.paymentMethod) {
      showToast("Please select a payment method.", "error");
      return;
    }

    // -----------------------------
    // Prepare receipt
    // -----------------------------

    state.plate = plate.toUpperCase();

    const now = new Date();

    const dateStr =
      now.toLocaleDateString() +
      " " +
      now.toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
      });

    const totalSum = state.services.reduce((acc, curr) => acc + curr.price, 0);

    const receiptData = {
      station: "SPARKLE EXPRESS CARWASH",
      unit: "Mobile Smart POS Unit",
      date: dateStr,
      ticket: state.ticket,
      plate: state.plate,
      vehicleType: state.vehicleType,
      washers: state.washers.join(", "),
      services: state.services,
      total: totalSum,
      paymentMethod: state.paymentMethod,
    };

    console.log("Receipt data:", receiptData);

    // -----------------------------
    // Native Android printer
    // -----------------------------

    if (window.Capacitor?.isNativePlatform?.()) {
      try {
        console.log("Sending receipt to CS30 printer...");

        await CS30Printer.printReceipt({
          receipt: receiptData,
        });

        console.log("Printed successfully via CS30 Hardware SDK.");

        showToast("Receipt printed successfully!", "success");
      } catch (error) {
        console.error("Printer execution error:", error);

        const message = error?.message || error?.error || String(error);

        showToast("Printing failed: " + message, "error");
      }

      return;
    }

    // -----------------------------
    // Browser/Web fallback
    // -----------------------------

    showToast("Web mode: sending to browser print spooler.", "info");

    window.print();
  }

  // Initial UI render
  updateSummary();
});
