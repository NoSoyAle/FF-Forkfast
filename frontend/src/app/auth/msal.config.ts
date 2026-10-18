import {
  IPublicClientApplication,
  PublicClientApplication,
  InteractionType,
  BrowserCacheLocation,
  LogLevel,
} from '@azure/msal-browser';
import {
  MsalGuardConfiguration,
  MsalInterceptorConfiguration,
} from '@azure/msal-angular';
import { environment } from '../../environments/environment';

export function MSALInstanceFactory(): IPublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId: environment.azureAd.clientId,
      authority: `https://login.microsoftonline.com/${environment.azureAd.tenantId}`,
      // window.location.origin se calcula en tiempo real (http://localhost,
      // http://localhost:4200, http://TU-IP-EC2, etc.) en vez de depender de
      // un valor fijo en environment.ts que se rompe apenas cambias de puerto
      // o de máquina.
      redirectUri: window.location.origin,
      postLogoutRedirectUri: window.location.origin,
    },
    cache: {
      cacheLocation: BrowserCacheLocation.LocalStorage,
      storeAuthStateInCookie: false,
    },
    system: {
      loggerOptions: {
        loggerCallback: (level, message) => {
          if (level <= LogLevel.Warning) {
            console.log(message);
          }
        },
      },
    },
  });
}

export function MSALGuardConfigFactory(): MsalGuardConfiguration {
  return {
    interactionType: InteractionType.Redirect,
    authRequest: {
      scopes: [environment.azureAd.apiScope],
    },
  };
}

export function MSALInterceptorConfigFactory(): MsalInterceptorConfiguration {
  const protectedResourceMap = new Map<string, Array<string>>();
  // Todo lo que apunte al backend viajará con el token adjunto.
  protectedResourceMap.set(environment.apiBaseUrl + '/*', [
    environment.azureAd.apiScope,
  ]);

  return {
    interactionType: InteractionType.Redirect,
    protectedResourceMap,
  };
}
