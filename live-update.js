(function(){
  window.RUSH_LIVE_VERSION='2026.09.27.2';
  window.RUSH_LIVE_UPDATE_READY=true;

  // Live update channel. Future UI/logic patches can be applied here
  // without changing the app package or clearing localStorage.
  try {
    window.dispatchEvent(new CustomEvent('rushliveupdate',{
      detail:{version:window.RUSH_LIVE_VERSION}
    }));
  } catch(e) {}
})();
