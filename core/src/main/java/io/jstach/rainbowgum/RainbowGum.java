package io.jstach.rainbowgum;

import java.util.List;

public interface RainbowGum {
	
	public LogConfig config();
	
	public LogRouter router();

	public class Builder {
		private LogConfig config = LogConfig.of(System::getProperty);
		private LogRouter router;
		
		private Builder() {
		}
		
		public Builder router(LogRouter router) {
			this.router = router;
			return this;
		}
		
		public Builder config(LogConfig config) {
			this.config = config;
			return this;
		}
		
		public RainbowGum build() {
			var router = this.router;
			var config = this.config;
			if (router == null) {
				router = LogRouter.of(List.of(LogAppender.of(config.defaultOutput(), null)));
			}
			return new RainbowGum() {

				@Override
				public LogRouter router() {
					// TODO Auto-generated method stub
					return null;
				}
				
				@Override
				public LogConfig config() {
					// TODO Auto-generated method stub
					return null;
				}
				
			};
		}
	}
}
