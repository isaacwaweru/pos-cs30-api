// State Management — everything starts empty (nothing pre-selected)
const state = {
  ticket: "SW-" + Math.floor(1000 + Math.random() * 9000),
  vehicleType: "",
  services: [],
  washers: [],
  plate: "",
  paymentMethod: "",
};

// Initialize UI Elements
document.getElementById("sumTicket").innerText = state.ticket;

// 1. Vehicle Type Selection Logic (single select, click again to clear)
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

// 2. Services Selection Logic (multi-select, free toggle)
const serviceItems = document.querySelectorAll(".service-item");
serviceItems.forEach((item) => {
  item.addEventListener("click", () => {
    item.classList.toggle("active");

    state.services = [];
    document.querySelectorAll(".service-item.active").forEach((activeItem) => {
      state.services.push({
        name: activeItem.getAttribute("data-name"),
        price: parseInt(activeItem.getAttribute("data-price")),
      });
    });

    updateSummary();
  });
});

// 3. Assign Washers Logic (multi-select, free toggle)
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

// 4. Plate Input & Payment Method Logic
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

// Calculation & Summary Update Function
function updateSummary() {
  const totalPrice = state.services.reduce((acc, curr) => acc + curr.price, 0);
  const commissionPerWasher = Math.round(totalPrice * 0.12); // e.g., 12% commission split model

  // Update UI elements
  document.getElementById("sumVehicle").innerText = state.vehicleType || "—";
  document.getElementById("sumServicesCount").innerText = state.services.length;
  document.getElementById("sumWashersCount").innerText = state.washers.length;
  document.getElementById("sumPayment").innerText = state.paymentMethod || "—";
  document.getElementById("sumTotal").innerText = `KSh ${totalPrice}`;
  document.getElementById("sumCommission").innerText =
    `KSh ${commissionPerWasher}`;
  document.getElementById("createJobBtn").innerText =
    `Create Job — KSh ${totalPrice}`;
}

// Print Receipt Function (CS30 Driver Integration)
function printReceipt() {
  // --- Validation: nothing is pre-selected, so make sure the job is complete ---
  if (!state.vehicleType) {
    alert("Please select a vehicle type.");
    return;
  }
  if (state.services.length === 0) {
    alert("Please select at least one service.");
    return;
  }
  if (state.washers.length === 0) {
    alert("Please assign at least one washer.");
    return;
  }

  const plate = plateInput.value.trim();
  if (!plate) {
    alert("Please enter a vehicle plate number.");
    plateInput.focus();
    return;
  }
  state.plate = plate;

  if (!state.paymentMethod) {
    alert("Please select a payment method.");
    return;
  }

  // Populate thermal receipt template fields
  const now = new Date();
  document.getElementById("p-date").innerText =
    now.toLocaleDateString() +
    " " +
    now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  document.getElementById("p-receiptno").innerText = state.ticket;
  document.getElementById("p-plate").innerText = state.plate;
  document.getElementById("p-size").innerText = state.vehicleType;
  document.getElementById("p-washers").innerText = state.washers.join(", ");

  // Build dynamic items list for thermal print format
  const servicesContainer = document.getElementById("p-services-list");
  servicesContainer.innerHTML = "";
  let totalSum = 0;

  state.services.forEach((srv) => {
    totalSum += srv.price;
    const row = document.createElement("div");
    row.className = "r-flex";
    row.innerHTML = `<span>${srv.name}</span><span>KES ${srv.price}</span>`;
    servicesContainer.appendChild(row);
  });

  document.getElementById("p-amount").innerText = totalSum;
  document.getElementById("p-payment").innerText = state.paymentMethod;

  // Trigger window print (triggers native CS30 built-in thermal printer output)
  window.print();
}

// Initial render (everything empty)
updateSummary();
