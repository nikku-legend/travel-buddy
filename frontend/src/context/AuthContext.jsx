import { useEffect, useState } from "react";

import authService from "../services/authService";
import userService from "../services/userService";
import { AuthContext } from "./useAuth";

const ACCESS_TOKEN_KEY = "accessToken";
const REFRESH_TOKEN_KEY = "refreshToken";

function clearAuthenticationStorage() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);

  const [loading, setLoading] = useState(() =>
    Boolean(getAccessToken())
  );

  /*
   * ============================================================
   * INITIAL AUTHENTICATION CHECK
   * ============================================================
   */
  useEffect(() => {
    let mounted = true;

    async function restoreAuthentication() {
      const accessToken = getAccessToken();

      /*
       * No token means the visitor is simply a guest.
       */
      if (!accessToken) {
        if (mounted) {
          setUser(null);
          setLoading(false);
        }

        return;
      }

      try {
        /*
         * Ask the backend who owns the current JWT.
         *
         * GET /api/v1/user/me
         */
        const currentUser = await userService.getCurrentUser();

        if (mounted) {
          setUser(currentUser);
        }
      } catch (error) {
        console.warn(
          "Stored authentication is no longer valid.",
          error
        );

        /*
         * Do not leave stale authentication information in the
         * browser when the backend rejects the token.
         */
        clearAuthenticationStorage();

        if (mounted) {
          setUser(null);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    restoreAuthentication();

    return () => {
      mounted = false;
    };
  }, []);

  /*
   * ============================================================
   * LOGIN
   * ============================================================
   */
  async function login(credentials) {
    const response = await authService.login(credentials);

    if (!response?.accessToken) {
      throw new Error(
        "Login succeeded but no access token was returned."
      );
    }

    /*
     * Store the authentication tokens.
     */
    localStorage.setItem(
      ACCESS_TOKEN_KEY,
      response.accessToken
    );

    if (response.refreshToken) {
      localStorage.setItem(
        REFRESH_TOKEN_KEY,
        response.refreshToken
      );
    }

    try {
      /*
       * Never rely only on the login response for profile data.
       *
       * /user/me is the source of truth for the authenticated
       * account.
       */
      const currentUser =
        await userService.getCurrentUser();

      setUser(currentUser);

      return {
        ...response,
        user: currentUser,
      };
    } catch (error) {
      clearAuthenticationStorage();
      setUser(null);

      throw error;
    }
  }

  /*
   * ============================================================
   * REGISTER
   * ============================================================
   */
  async function register(registerData) {
    const response =
      await authService.register(registerData);

    if (!response?.accessToken) {
      throw new Error(
        "Registration succeeded but no access token was returned."
      );
    }

    localStorage.setItem(
      ACCESS_TOKEN_KEY,
      response.accessToken
    );

    if (response.refreshToken) {
      localStorage.setItem(
        REFRESH_TOKEN_KEY,
        response.refreshToken
      );
    }

    try {
      const currentUser =
        await userService.getCurrentUser();

      setUser(currentUser);

      return {
        ...response,
        user: currentUser,
      };
    } catch (error) {
      clearAuthenticationStorage();
      setUser(null);

      throw error;
    }
  }

  /*
   * ============================================================
   * LOGOUT
   * ============================================================
   */
  async function logout() {
    const refreshToken =
      localStorage.getItem(REFRESH_TOKEN_KEY);

    try {
      /*
       * Tell the backend to revoke the refresh token.
       */
      if (refreshToken) {
        await authService.logout(refreshToken);
      }
    } catch (error) {
      /*
       * A failed backend logout must NOT prevent local logout.
       *
       * The user should still immediately become a guest.
       */
      console.warn(
        "Backend logout request failed. Clearing local authentication.",
        error
      );
    } finally {
      /*
       * Always clear both tokens.
       */
      clearAuthenticationStorage();

      /*
       * Immediately remove the authenticated user from React state.
       */
      setUser(null);
    }
  }

  /*
   * ============================================================
   * REFRESH PROFILE
   *
   * Re-reads /user/me from the server.
   *
   * Needed because roles are not baked into the JWT: when an admin
   * approves a partner application the new role appears here on the next
   * call, and the navbar and route guards immediately pick it up without
   * forcing the user to sign in again.
   * ============================================================
   */
  async function refreshProfile() {
    const currentUser = await userService.getCurrentUser();

    setUser(currentUser);

    return currentUser;
  }

  /*
   * ============================================================
   * AUTHENTICATION CONTEXT
   * ============================================================
   */
  const contextValue = {
    user,
    loading,
    isAuthenticated: Boolean(user),
    login,
    register,
    logout,
    refreshProfile,
  };

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
}