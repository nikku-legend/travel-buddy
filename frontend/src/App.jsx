import { Route, Routes } from "react-router-dom";

import PublicLayout from "./layouts/PublicLayout";
import ProtectedRoute from "./components/ProtectedRoute";
import Bookings from "./pages/Bookings";
import Home from "./pages/Home";
import Destinations from "./pages/Destinations";
import DestinationDetails from "./pages/DestinationDetails";
import BucketList from "./pages/BucketList";
import Stays from "./pages/Stays";
import StayDetails from "./pages/StayDetails";
import Guides from "./pages/Guides";
import Checkout from "./pages/Checkout";
import BookingDetails from "./pages/BookingDetails";
import MockPayment from "./pages/MockPayment";
import Profile from "./pages/Profile";
import Login from "./pages/Login";
import Register from "./pages/Register";

import HotelPartnerDashboard from "./pages/partner/HotelPartnerDashboard";
import GuidePartnerDashboard from "./pages/partner/GuidePartnerDashboard";
import CabPartnerDashboard from "./pages/partner/CabPartnerDashboard";
import BecomePartner from "./pages/partner/BecomePartner";
import PartnerApplication from "./pages/partner/PartnerApplication";
import AdminDashboard from "./pages/admin/AdminDashboard";
import Trips from "./pages/Trips";
import TripDetail from "./pages/TripDetail";
import TripPlanner from "./pages/TripPlanner";

export default function App() {
  return (
    <Routes>
      {/* =========================================================
          PUBLIC LAYOUT
         ========================================================= */}

      <Route element={<PublicLayout />}>
        {/* ---------------------------------------------------------
            PUBLIC PAGES
           --------------------------------------------------------- */}

        <Route
          path="/"
          element={<Home />}
        />

        <Route
          path="/destinations"
          element={<Destinations />}
        />

        <Route
          path="/destinations/:id"
          element={<DestinationDetails />}
        />

        <Route
          path="/bucket-list"
          element={<BucketList />}
        />

        <Route
          path="/stays"
          element={<Stays />}
        />

        <Route
          path="/stays/:id"
          element={<StayDetails />}
        />

        <Route
          path="/guides"
          element={<Guides />}
        />

        {/* ---------------------------------------------------------
            TRIP PLANNER
           The treasure map, the route and the cart are
           returned together by the planner, so these read as one
           journey rather than three separate screens.
           --------------------------------------------------------- */}

        <Route
          path="/trips"
          element={<Trips />}
        />

        <Route
          path="/trips/new"
          element={<TripPlanner />}
        />

        <Route
          path="/trip-planner"
          element={<TripPlanner />}
        />

        <Route
          path="/trips/:tripId"
          element={<TripDetail />}
        />

        {/* ---------------------------------------------------------
            PARTNER ONBOARDING

            Public so a guest can see what each partner type needs
            before deciding to sign up. Submitting an application
            requires an account.
         --------------------------------------------------------- */}

        <Route
          path="/become-a-partner"
          element={<BecomePartner />}
        />

        {/* ---------------------------------------------------------
            AUTHENTICATION
           --------------------------------------------------------- */}

        <Route
          path="/login"
          element={<Login />}
        />

        <Route
          path="/register"
          element={<Register />}
        />

        {/* =========================================================
            PROTECTED APPLICATION (TRAVELER)
           ========================================================= */}

        <Route element={<ProtectedRoute />}>
          {/* Profile */}
          <Route
            path="/profile"
            element={<Profile />}
          />

          {/* Booking history */}
          <Route
            path="/bookings"
            element={<Bookings />}
          />

          {/* Checkout */}
          <Route
            path="/checkout"
            element={<Checkout />}
          />

          {/* Individual booking */}
          <Route
            path="/bookings/:bookingId"
            element={<BookingDetails />}
          />

          {/* Development payment gateway */}
          <Route
            path="/checkout/payment/mock"
            element={<MockPayment />}
          />

          {/* Existing compatibility route */}
          <Route
            path="/checkout/payment"
            element={<MockPayment />}
          />

          {/* Partner application form. Any signed-in user may
              apply; the partner role arrives only after admin
              approval, which is why this is inside the generic
              protected block and not the partner portals. */}
          <Route
            path="/partner/apply/:partnerType"
            element={<PartnerApplication />}
          />
        </Route>

        {/* =========================================================
            PROTECTED PARTNER PORTALS
           ========================================================= */}

        {/* Hotel Partner Portal */}
        <Route
          element={
            <ProtectedRoute
              allowedRoles={["ROLE_HOTEL_PARTNER", "ROLE_SUPER_ADMIN"]}
            />
          }
        >
          <Route
            path="/partner/hotel"
            element={<HotelPartnerDashboard />}
          />
        </Route>

        {/* Guide Partner Portal */}
        <Route
          element={
            <ProtectedRoute
              allowedRoles={["ROLE_GUIDE_PARTNER", "ROLE_SUPER_ADMIN"]}
            />
          }
        >
          <Route
            path="/partner/guide"
            element={<GuidePartnerDashboard />}
          />
        </Route>

        {/* Cab / Transport Partner Portal */}
        <Route
          element={
            <ProtectedRoute
              allowedRoles={["ROLE_CAB_PARTNER", "ROLE_SUPER_ADMIN"]}
            />
          }
        >
          <Route
            path="/partner/cab"
            element={<CabPartnerDashboard />}
          />
        </Route>

        {/* =========================================================
            PROTECTED SUPER ADMIN CONSOLE
           ========================================================= */}

        <Route
          element={
            <ProtectedRoute allowedRoles={["ROLE_SUPER_ADMIN"]} />
          }
        >
          <Route
            path="/admin"
            element={<AdminDashboard />}
          />
        </Route>

        {/* ---------------------------------------------------------
            FALLBACK
           --------------------------------------------------------- */}

        <Route
          path="*"
          element={
            <div className="mx-auto flex min-h-[70vh] max-w-7xl items-center justify-center px-5">
              <div className="text-center">
                <p className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-400">
                  404
                </p>

                <h1 className="mt-3 text-4xl font-bold text-slate-900">
                  Page not found
                </h1>

                <p className="mt-3 text-sm text-slate-500">
                  The page you are looking for does not exist.
                </p>
              </div>
            </div>
          }
        />
      </Route>
    </Routes>
  );
}
